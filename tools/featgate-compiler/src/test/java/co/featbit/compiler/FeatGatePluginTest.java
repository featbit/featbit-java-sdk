package co.featbit.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatGatePluginTest {
    private static final String IMPORT = "import co.featbit.server.integrations.FeatGate;\n";
    private static final String OLD = "@FeatGate(flag=\"name\",value=\"default\") @FeatGate(flag=\"name\",value=\"v1\") ";
    private static final String NEW = "@FeatGate(flag=\"name\",value=\"v2\") ";
    @TempDir Path temporary;

    @Test
    void duplicatePrivateMethodsRouteThroughInterfaceAndSelfCalls() throws Exception {
        String source = IMPORT + "interface Api {String name(String who);}\npublic class Demo implements Api {"
                + OLD + "private String name(String who) {return \"old:\"+who;}"
                + NEW + "private String name(String who) {return \"new:\"+who;}"
                + "public String self(String who) {return name(who);}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            assertEquals("old:Joe", call(target, "name", "Joe"));
            Object client = compiled.bind(target, "v2");
            assertEquals("new:Joe", call(target, "name", "Joe"));
            assertEquals("new:Joe", call(target, "self", "Joe"));
            compiled.variant(client, "unknown");
            assertEquals("old:Joe", call(target, "name", "Joe"));
            compiled.variant(client, "v1");
            assertEquals("old:Joe", call(target, "name", "Joe"));
            Field field = target.getClass().getDeclaredField("$featbit$client");
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isTransient(field.getModifiers()));
            assertTrue(Modifier.isVolatile(field.getModifiers()));
        }
        assertFalse(compileResult(source, false).success);
    }

    @Test
    void cleanupOnlyDeletesLinesAndKeepsTheSameCallerAndBinding() throws Exception {
        String oldBlock = OLD + "private String name(String who) {return \"old:\"+who;}\n";
        String source = IMPORT + "interface Api {String name(String who);}\npublic class Demo implements Api {\n"
                + oldBlock + NEW + "\nprivate String name(String who) {return \"new:\"+who;}\n"
                + "public String self(String who) {return name(who);}\n}";
        String cleaned = source.replace(oldBlock, "").replace(NEW + "\n", "");
        try (Compilation before = compile(source, true); Compilation after = compile(cleaned, true)) {
            Object original = before.newInstance("Demo");
            before.bind(original, "v2");
            Object retained = after.newInstance("Demo");
            after.bind(retained, "v1");
            assertEquals(call(original, "name", "Joe"), call(retained, "name", "Joe"));
            assertEquals("new:Joe", call(retained, "self", "Joe"));
            assertEquals(0, retained.getClass().getDeclaredFields().length);
        }
        assertFalse(compileResult(cleaned, false).success);
    }

    @Test
    void superclassAndSubclassDispatchUseTheirOwnBoundFields() throws Exception {
        String source = IMPORT + "class Base {"
                + OLD + "public String name(String who){return \"old-base\";}"
                + NEW + "public String name(String who){return \"new-base\";} }"
                + "public class Demo extends Base {"
                + OLD + "public String second(String who){return \"old-child\";}"
                + NEW + "public String second(String who){return \"new-child\";} }";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            compiled.bind(target, "v2");
            assertEquals("new-base", call(target, "name", "x"));
            assertEquals("new-child", call(target, "second", "x"));
            assertNotNull(target.getClass().getDeclaredField("$featbit$client"));
            assertNotNull(target.getClass().getSuperclass().getDeclaredField("$featbit$client"));
        }
    }

    @Test
    void genericVarargsPrimitiveVoidAndCheckedExceptionsWork() throws Exception {
        String source = IMPORT + "interface Api { <T extends Number> T pick(T value); int sum(int... values); void act(String value) throws java.io.IOException;}"
                + "public class Demo implements Api {public int count;"
                + OLD + "private <T extends Number> T pick(T value){return value;}"
                + NEW + "private <T extends Number> T pick(T other){return other;}"
                + OLD + "private int sum(int... values){return values.length;}"
                + NEW + "private int sum(int... other){return other.length*10;}"
                + OLD + "private void act(String value) throws java.io.IOException {count++;}"
                + NEW + "private void act(String other) throws java.io.IOException {count+=10; if(other.equals(\"fail\"))throw new java.io.IOException(\"selected\");}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            compiled.bind(target, "v2");
            assertEquals(7, target.getClass().getMethod("pick", Number.class).invoke(target, 7));
            assertEquals(20, target.getClass().getMethod("sum", int[].class).invoke(target, new int[]{1, 2}));
            call(target, "act", "ok");
            assertEquals(10, target.getClass().getField("count").get(target));
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> call(target, "act", "fail"));
            assertEquals(IOException.class, error.getCause().getClass());
            assertEquals("selected", error.getCause().getMessage());
            assertEquals(20, target.getClass().getField("count").get(target));
        }
    }

    @Test
    void overloadsAndIndependentFlagsRemainSeparate() throws Exception {
        String source = IMPORT + "public class Demo {"
                + OLD + "public String name(String value){return \"old-string\";}"
                + NEW + "public String name(String value){return \"new-string\";}"
                + OLD.replace("name", "number") + "public int name(int value){return value;}"
                + NEW.replace("name", "number") + "public int name(int value){return value*10;} }";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            compiled.bind(target, "v2");
            assertEquals("new-string", call(target, "name", "x"));
            assertEquals(30, target.getClass().getMethod("name", int.class).invoke(target, 3));
        }
    }

    @Test
    void wrapperKeepsAnnotationsAndSynchronizedAndHelpersKeepStrictFloatingPoint() throws Exception {
        String source = IMPORT + "public class Demo {"
                + OLD + "@Deprecated public synchronized strictfp String name(String value){if(!Thread.holdsLock(this))throw new AssertionError();return \"old\";}"
                + NEW + "@Deprecated public synchronized strictfp String name(String value){if(!Thread.holdsLock(this))throw new AssertionError();return \"new\";} }";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            compiled.bind(target, "v2");
            assertEquals("new", call(target, "name", "x"));
            Method wrapper = target.getClass().getMethod("name", String.class);
            assertTrue(wrapper.isAnnotationPresent(Deprecated.class));
            assertTrue(Modifier.isSynchronized(wrapper.getModifiers()));
            for (Method method : target.getClass().getDeclaredMethods()) {
                if (method.getName().startsWith("$featbit$gate")) {
                    assertTrue(Modifier.isPrivate(method.getModifiers()));
                    assertFalse(Modifier.isSynchronized(method.getModifiers()));
                    assertEquals(0, method.getDeclaredAnnotations().length);
                    if (System.getProperty("java.specification.version").equals("1.8")) {
                        assertTrue(Modifier.isStrict(method.getModifiers()));
                    }
                }
            }
        }
    }

    @Test
    void cleanupResolvesInheritedGenericInterfaceContracts() throws Exception {
        String source = "interface Api<T>{T name(T value);} abstract class Base implements Api<String>{}"
                + "public class Demo extends Base {private String name(String value){return value.toUpperCase();} private String helper(){return \"private\";}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            assertEquals("ALBERT", call(target, "name", "albert"));
            assertTrue(Modifier.isPrivate(target.getClass().getDeclaredMethod("helper").getModifiers()));
        }
    }

    @Test
    void validProgramsAndStaticInterfaceHelpersAreUntouched() throws Exception {
        String source = "interface Api {static String name(String value){return value;}} public class Demo implements Api {"
                + "private String name(String value){return value;} public String call(String value){return name(value);}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            assertEquals("x", call(target, "call", "x"));
            assertTrue(Modifier.isPrivate(target.getClass().getDeclaredMethod("name", String.class).getModifiers()));
            assertEquals(0, target.getClass().getDeclaredFields().length);
        }
    }

    @Test
    void existingVariantApiIsNotTransformed() throws Exception {
        String source = IMPORT + "public class Demo { @FeatGate(flag=\"name\",variants={@FeatGate.Variant(value=\"v2\",method=\"other\")})"
                + "public String name(String value){return \"original\";} private String other(String value){return \"other\";}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            assertEquals("original", call(target, "name", "x"));
            assertEquals(0, target.getClass().getDeclaredFields().length);
            assertEquals(1, target.getClass().getMethod("name", String.class).getDeclaredAnnotations().length);
        }
    }

    @Test
    void fullyQualifiedAndWildcardImportedAnnotationsWork() throws Exception {
        for (String importLine : Arrays.asList("", "import co.featbit.server.integrations.*;")) {
            String annotation = importLine.isEmpty() ? "@co.featbit.server.integrations.FeatGate" : "@FeatGate";
            String source = importLine + " public class Demo {"
                    + OLD.replace("@FeatGate", annotation) + "public String name(String value){return \"old\";}"
                    + NEW.replace("@FeatGate", annotation) + "public String name(String value){return \"new\";} }";
            try (Compilation compiled = compile(source, true)) {
                Object target = compiled.newInstance("Demo");
                compiled.bind(target, "v2");
                assertEquals("new", call(target, "name", "x"));
            }
        }
    }

    @Test
    void unrelatedNestedAnnotationIsNotTransformed() throws Exception {
        String source = IMPORT + "public class Demo { @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)"
                + "@interface FeatGate {String flag(); String value();}"
                + "@FeatGate(flag=\"unrelated\",value=\"default\") private String name(String value){return value;} }";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            Method method = target.getClass().getDeclaredMethod("name", String.class);
            assertTrue(Modifier.isPrivate(method.getModifiers()));
            assertEquals(1, method.getDeclaredAnnotations().length);
            assertEquals(0, target.getClass().getDeclaredFields().length);
        }
    }

    @Test
    void inheritedUnrelatedAnnotationAndLegalGenericOverloadsAreUntouched() throws Exception {
        String source = IMPORT + "class Base { @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)"
                + "@interface FeatGate {String flag(); String value();}} public class Demo extends Base {"
                + "@FeatGate(flag=\"unrelated\",value=\"default\") private <T extends Number> T name(T value){return value;}"
                + "@FeatGate(flag=\"unrelated\",value=\"v2\") private <T extends CharSequence> T name(T value){return value;}}";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            assertEquals(0, target.getClass().getDeclaredFields().length);
            for (Method method : target.getClass().getDeclaredMethods()) {
                assertTrue(Modifier.isPrivate(method.getModifiers()));
                assertEquals(1, method.getDeclaredAnnotations().length);
            }
        }
    }

    @Test
    void generatedRuntimeTypeIsNotShadowedByAnOrdinaryField() throws Exception {
        String source = IMPORT + "public class Demo {private Object co;"
                + OLD + "public String name(String value){return \"old\";}"
                + NEW + "public String name(String value){return \"new\";} }";
        try (Compilation compiled = compile(source, true)) {
            Object target = compiled.newInstance("Demo");
            compiled.bind(target, "v2");
            assertEquals("new", call(target, "name", "x"));
        }
    }

    @Test
    void malformedGroupsFailWithActionableDiagnostics() throws Exception {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("exactly one", NEW + "public String name(String value){return value;}" + NEW.replace("v2", "v3") + "public String name(String value){return value;}");
        cases.put("Duplicate feature gate value", OLD + "public String name(String value){return value;}" + NEW.replace("v2", "v1") + "public String name(String value){return value;}");
        cases.put("same flag", OLD + "public String name(String value){return value;}" + NEW.replace("name", "other") + "public String name(String value){return value;}");
        cases.put("All declarations", OLD + "public String name(String value){return value;}public String name(String value){return value;}");
        cases.put("identical modifiers", OLD + "public synchronized String name(String value){return value;}" + NEW + "public String name(String value){return value;}");
        cases.put("concrete instance", OLD + "public static String name(String value){return value;}" + NEW + "public static String name(String value){return value;}");
        cases.put("reserved", "private int $featbit$client;" + OLD + "public String name(String value){return value;}" + NEW + "public String name(String value){return value;}");
        cases.put("string literals", "private static final String KEY=\"name\";" + OLD.replace("flag=\"name\"", "flag=KEY") + "public String name(String value){return value;}" + NEW + "public String name(String value){return value;}");
        cases.put("not both", "@FeatGate(flag=\"name\",value=\"default\",variants={})public String name(String value){return value;}" + NEW + "public String name(String value){return value;}");
        cases.put("unique flag and value", OLD.replace("value=\"default\"", "value=\"default\",unknown=\"bad\"") + "public String name(String value){return value;}" + NEW + "public String name(String value){return value;}");
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            CompilationResult result = compileResult(IMPORT + "public class Demo {" + entry.getValue() + "}", true);
            assertFalse(result.success, entry.getKey());
            assertTrue(result.messages.contains("FeatGate:") && result.messages.contains(entry.getKey()), result.messages);
        }
    }

    private static Object call(Object target, String method, String value) throws Exception {
        Method selected = target.getClass().getMethod(method, String.class);
        selected.setAccessible(true);
        return selected.invoke(target, value);
    }

    private Compilation compile(String source, boolean enabled) throws Exception {
        CompilationResult result = compileResult(source, enabled);
        assertTrue(result.success, result.messages);
        return new Compilation(result.output);
    }

    private CompilationResult compileResult(String source, boolean enabled) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path output = Files.createTempDirectory(temporary, "compiled-");
        List<String> options = new ArrayList<>(Arrays.asList("-d", output.toString(), "-classpath", System.getProperty("java.class.path"), "-source", "8", "-target", "8"));
        if (enabled) {
            options.add("-Xplugin:FeatGate");
        }
        List<JavaFileObject> sources = new ArrayList<>();
        sources.add(new Source("Demo", source));
        sources.add(new Source("co.featbit.server.integrations.FeatGate", "package co.featbit.server.integrations; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @Repeatable(FeatGate.Gates.class) public @interface FeatGate {String flag(); String value() default \"\"; Variant[] variants() default {}; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Gates {FeatGate[] value();} @interface Variant {String value();String method();}}"));
        sources.add(new Source("co.featbit.server.exterior.FBClient", "package co.featbit.server.exterior; public class FBClient { public volatile String value; }"));
        sources.add(new Source("co.featbit.server.integrations.FeatGateSupport", "package co.featbit.server.integrations; import java.lang.annotation.*; import co.featbit.server.exterior.FBClient; public class FeatGateSupport { @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Client {} public static String variation(FBClient client,String flag,String fallback){return client==null?fallback:client.value;} }"));
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean success = compiler.getTask(null, manager, diagnostics, options, null, sources).call();
            return new CompilationResult(success, diagnostics.getDiagnostics().toString(), output);
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String text;
        Source(String name, String text) {
            super(URI.create("string:///" + name.replace('.', '/') + ".java"), Kind.SOURCE);
            this.text = text;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    private static final class CompilationResult {
        private final boolean success;
        private final String messages;
        private final Path output;
        CompilationResult(boolean success, String messages, Path output) {
            this.success = success;
            this.messages = messages;
            this.output = output;
        }
    }

    private static final class Compilation implements AutoCloseable {
        private final URLClassLoader loader;
        Compilation(Path output) throws Exception {
            loader = new URLClassLoader(new URL[]{output.toUri().toURL()}, null);
        }
        Object newInstance(String name) throws Exception {
            return loader.loadClass(name).getDeclaredConstructor().newInstance();
        }
        Object bind(Object target, String value) throws Exception {
            Object client = newInstance("co.featbit.server.exterior.FBClient");
            variant(client, value);
            for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getName().equals("$featbit$client")) {
                        field.setAccessible(true);
                        field.set(target, client);
                    }
                }
            }
            return client;
        }
        void variant(Object client, String value) throws Exception {
            client.getClass().getField("value").set(client, value);
        }
        @Override
        public void close() throws IOException {
            loader.close();
        }
    }
}
