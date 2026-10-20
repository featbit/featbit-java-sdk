package co.featbit.compiler;

import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FeatGatePlugin implements Plugin {
    private static final String ANNOTATION = "co.featbit.server.integrations.FeatGate";
    private static final String SUPPORT = "co.featbit.server.integrations.FeatGateSupport";
    private static final String CLIENT = "co.featbit.server.exterior.FBClient";
    private static final String PREFIX = "$featbit$";
    private static final long PUBLIC = 1L;
    private static final long PRIVATE = 2L;
    private static final long PROTECTED = 4L;
    private static final long STATIC = 8L;
    private static final long NATIVE = 256L;
    private static final long INTERFACE = 512L;
    private static final long ABSTRACT = 1024L;

    @Override
    public String getName() {
        return "FeatGate";
    }

    @Override
    public void init(final JavacTask task, String... args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("FeatGate does not accept plugin arguments");
        }
        final Context context = ((BasicJavacTask) task).getContext();
        final Trees trees = Trees.instance(task);
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent event) {
            }

            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.PARSE) {
                    new Transformer(context, trees, (JCCompilationUnit) event.getCompilationUnit()).scan((JCCompilationUnit) event.getCompilationUnit());
                } else if (event.getKind() == TaskEvent.Kind.ENTER) {
                    promoteInterfaceImplementations(task, (JCCompilationUnit) event.getCompilationUnit());
                }
            }
        });
    }

    private static void promoteInterfaceImplementations(final JavacTask task, JCCompilationUnit unit) {
        new TreeScanner() {
            @Override
            public void visitClassDef(JCClassDecl declaration) {
                TypeElement owner = (TypeElement) (Object) declaration.sym;
                if (owner != null && owner.getKind().isClass()) {
                    java.util.List<DeclaredType> interfaces = new ArrayList<>();
                    collectInterfaces(task, owner.asType(), interfaces, new HashSet<String>());
                    for (JCTree definition : declaration.defs) {
                        if (!(definition instanceof JCMethodDecl)) {
                            continue;
                        }
                        JCMethodDecl method = (JCMethodDecl) definition;
                        if ((method.mods.flags & PRIVATE) == 0 || (method.mods.flags & STATIC) != 0 || method.sym == null) {
                            continue;
                        }
                        ExecutableElement implementation = (ExecutableElement) (Object) method.sym;
                        for (DeclaredType iface : interfaces) {
                            TypeElement type = (TypeElement) task.getTypes().asElement(iface);
                            for (Element member : task.getElements().getAllMembers(type)) {
                                if (member.getKind() == ElementKind.METHOD
                                        && member.getModifiers().contains(Modifier.PUBLIC)
                                        && !member.getModifiers().contains(Modifier.STATIC)
                                        && method.name.contentEquals(member.getSimpleName())
                                        && task.getTypes().isSubsignature((ExecutableType) implementation.asType(),
                                                (ExecutableType) task.getTypes().asMemberOf(iface, member))) {
                                    method.mods.flags = (method.mods.flags & ~(PRIVATE | PROTECTED)) | PUBLIC;
                                    Symbol symbol = (Symbol) (Object) method.sym;
                                    symbol.flags_field = (symbol.flags_field & ~(PRIVATE | PROTECTED)) | PUBLIC;
                                }
                            }
                        }
                    }
                }
                super.visitClassDef(declaration);
            }
        }.scan(unit);
    }

    private static void collectInterfaces(JavacTask task, TypeMirror type, java.util.List<DeclaredType> result, Set<String> seen) {
        if (type.getKind() != TypeKind.DECLARED || !seen.add(type.toString())) {
            return;
        }
        if (((TypeElement) task.getTypes().asElement(type)).getKind() == ElementKind.INTERFACE) {
            result.add((DeclaredType) type);
        }
        for (TypeMirror parent : task.getTypes().directSupertypes(type)) {
            collectInterfaces(task, parent, result, seen);
        }
    }

    private static final class Transformer extends TreeScanner {
        private final Context context;
        private final Trees trees;
        private final JCCompilationUnit unit;
        private final Names names;
        private final TreeCopier<Void> copier;
        private final boolean simpleName;
        private boolean shadowed;

        private Transformer(Context context, Trees trees, JCCompilationUnit unit) {
            this.context = context;
            this.trees = trees;
            this.unit = unit;
            this.names = Names.instance(context);
            this.copier = new TreeCopier<>(TreeMaker.instance(context));
            boolean imported = ANNOTATION.substring(0, ANNOTATION.lastIndexOf('.')).equals(
                    ((com.sun.source.tree.CompilationUnitTree) unit).getPackageName() == null ? "" : ((com.sun.source.tree.CompilationUnitTree) unit).getPackageName().toString());
            boolean conflicting = false;
            for (JCTree definition : unit.defs) {
                if (definition instanceof JCImport) {
                    JCImport item = (JCImport) definition;
                    String name = ((com.sun.source.tree.ImportTree) item).getQualifiedIdentifier().toString();
                    if (!item.staticImport && (name.equals(ANNOTATION) || name.equals("co.featbit.server.integrations.*"))) {
                        imported = true;
                    } else if (!item.staticImport && name.endsWith(".FeatGate")) {
                        conflicting = true;
                    }
                } else if (definition instanceof JCClassDecl
                        && ((JCClassDecl) definition).name.contentEquals("FeatGate")) {
                    conflicting = true;
                }
            }
            this.simpleName = imported && !conflicting;
        }

        @Override
        public void visitClassDef(JCClassDecl declaration) {
            boolean previousShadow = shadowed;
            for (JCTree definition : declaration.defs) {
                if (definition instanceof JCClassDecl && ((JCClassDecl) definition).name.contentEquals("FeatGate")) {
                    shadowed = true;
                }
            }
            Map<String, java.util.List<JCMethodDecl>> allMethods = new LinkedHashMap<>();
            Map<String, Group> groups = new LinkedHashMap<>();
            boolean valid = true;
            for (JCTree definition : declaration.defs) {
                if (!(definition instanceof JCMethodDecl)) {
                    continue;
                }
                JCMethodDecl method = (JCMethodDecl) definition;
                if (method.name.contentEquals("<init>")) {
                    continue;
                }
                String signature = signature(method);
                java.util.List<JCMethodDecl> methods = allMethods.get(signature);
                if (methods == null) {
                    allMethods.put(signature, methods = new ArrayList<>());
                }
                methods.add(method);
            }
            for (Map.Entry<String, java.util.List<JCMethodDecl>> entry : allMethods.entrySet()) {
                if (entry.getValue().size() < 2) {
                    continue;
                }
                for (JCMethodDecl method : entry.getValue()) {
                    for (JCAnnotation annotation : method.mods.annotations) {
                        if (!isGate(annotation)) {
                            continue;
                        }
                        Gate gate = read(annotation);
                        if (gate == null) {
                            continue;
                        }
                        if (gate.flag == null || gate.value == null) {
                            valid = false;
                            continue;
                        }
                        Group group = groups.get(entry.getKey());
                        if (group == null) {
                            groups.put(entry.getKey(), group = new Group(method));
                        }
                        if (!group.add(method, gate)) {
                            valid = false;
                        }
                    }
                }
            }
            if (!groups.isEmpty()) {
                if ((declaration.mods.flags & INTERFACE) != 0 || declaration.getKind().name().equals("RECORD")) {
                    error(declaration, "Method feature gates require a class, not an interface or record");
                    valid = false;
                }
                for (JCTree definition : declaration.defs) {
                    String name = definition instanceof JCMethodDecl ? ((JCMethodDecl) definition).name.toString()
                            : definition instanceof JCVariableDecl ? ((JCVariableDecl) definition).name.toString() : "";
                    if (name.startsWith(PREFIX)) {
                        error(definition, "Names beginning with " + PREFIX + " are reserved for generated feature gates");
                        valid = false;
                    }
                }
                for (Map.Entry<String, Group> entry : groups.entrySet()) {
                    if (!entry.getValue().validate(allMethods.get(entry.getKey()))) {
                        valid = false;
                    }
                }
                if (valid) {
                    declaration.defs = declaration.defs.append(clientField());
                    int index = 0;
                    for (Group group : groups.values()) {
                        declaration.defs = declaration.defs.append(group.generate(index++));
                    }
                }
            }
            super.visitClassDef(declaration);
            shadowed = previousShadow;
        }

        private boolean isGate(JCAnnotation annotation) {
            String name = annotation.annotationType.toString();
            return name.equals(ANNOTATION) || (simpleName && !shadowed && name.equals("FeatGate"));
        }

        private Gate read(JCAnnotation annotation) {
            JCExpression flag = null;
            JCExpression value = null;
            boolean legacy = false;
            boolean invalid = false;
            Set<String> arguments = new HashSet<>();
            for (JCExpression argument : annotation.args) {
                if (!(argument instanceof JCAssign)) {
                    invalid = true;
                    continue;
                }
                JCAssign assignment = (JCAssign) argument;
                String name = assignment.lhs.toString();
                if (!arguments.add(name)) {
                    invalid = true;
                }
                if (name.equals("flag")) {
                    flag = assignment.rhs;
                } else if (name.equals("value")) {
                    value = assignment.rhs;
                } else if (name.equals("variants")) {
                    legacy = true;
                } else {
                    invalid = true;
                }
            }
            if (value == null) {
                return null;
            }
            if (invalid) {
                error(annotation, "Feature gate arguments must be unique flag and value attributes");
                return new Gate(null, null, annotation);
            }
            if (legacy) {
                error(annotation, "Use value or variants on a feature gate, not both");
                return new Gate(null, null, annotation);
            }
            if (!(flag instanceof JCLiteral) || !(((JCLiteral) flag).getValue() instanceof String)
                    || !(value instanceof JCLiteral) || !(((JCLiteral) value).getValue() instanceof String)) {
                error(annotation, "Compiler feature gates require string literals for flag and value");
                return new Gate(null, null, annotation);
            }
            String key = (String) ((JCLiteral) flag).getValue();
            String variant = (String) ((JCLiteral) value).getValue();
            if (key.trim().isEmpty() || variant.isEmpty()) {
                error(annotation, "Feature gate flag and value must not be empty");
                return new Gate(null, null, annotation);
            }
            return new Gate(key, variant, annotation);
        }

        private String signature(JCMethodDecl method) {
            StringBuilder result = new StringBuilder().append(method.typarams).append(method.name).append('(');
            for (JCVariableDecl parameter : method.params) {
                result.append(parameter.vartype).append(';');
            }
            return result.append(')').toString();
        }

        private String metadata(JCMethodDecl method) {
            StringBuilder result = new StringBuilder().append(method.mods.flags).append('|')
                    .append(method.restype).append('|').append(method.typarams).append('|').append(method.thrown);
            for (JCAnnotation annotation : method.mods.annotations) {
                if (!isGate(annotation)) {
                    result.append('|').append(annotation);
                }
            }
            for (JCVariableDecl parameter : method.params) {
                result.append('|').append(parameter.mods).append(parameter.vartype);
            }
            return result.toString();
        }

        private JCVariableDecl clientField() {
            String source = "class Generated { @" + SUPPORT + ".Client private transient volatile "
                    + CLIENT + " " + PREFIX + "client; }";
            return (JCVariableDecl) parseClass(source).defs.head;
        }

        private JCClassDecl parseClass(String source) {
            return (JCClassDecl) ParserFactory.instance(context).newParser(source, false, false, false)
                    .parseCompilationUnit().defs.head;
        }

        private JCBlock parseBody(String source) {
            return (JCBlock) ParserFactory.instance(context).newParser(source, false, false, false).parseStatement();
        }

        private void error(Tree tree, String message) {
            trees.printMessage(Diagnostic.Kind.ERROR, "FeatGate: " + message, tree, unit);
        }

        private final class Group {
            private final JCMethodDecl first;
            private final java.util.List<JCMethodDecl> methods = new ArrayList<>();
            private final Map<String, JCMethodDecl> variants = new LinkedHashMap<>();
            private String flag;
            private JCMethodDecl fallback;
            private String fallbackValue;

            private Group(JCMethodDecl first) {
                this.first = first;
            }

            private boolean add(JCMethodDecl method, Gate gate) {
                boolean valid = true;
                if (flag == null) {
                    flag = gate.flag;
                } else if (!flag.equals(gate.flag)) {
                    error(gate.annotation, "All variants of a method must use the same flag");
                    valid = false;
                }
                if (variants.put(gate.value, method) != null) {
                    error(gate.annotation, "Duplicate feature gate value: " + gate.value);
                    valid = false;
                }
                if (!methods.contains(method)) {
                    methods.add(method);
                }
                if (gate.value.equals("default")) {
                    fallback = method;
                }
                return valid;
            }

            private boolean validate(java.util.List<JCMethodDecl> declarations) {
                boolean valid = true;
                if (fallback == null) {
                    error(first, "Each method group needs exactly one value=\"default\" annotation");
                    valid = false;
                }
                if (declarations.size() != methods.size()) {
                    error(first, "All declarations of a gated method must use value-based feature gate annotations");
                    valid = false;
                }
                String expected = metadata(first);
                for (JCMethodDecl method : methods) {
                    if ((method.mods.flags & (STATIC | ABSTRACT | NATIVE)) != 0 || method.body == null) {
                        error(method, "Feature gate variants must be concrete instance methods");
                        valid = false;
                    }
                    if (!expected.equals(metadata(method))) {
                        error(method, "Feature gate variants must have identical modifiers, return type, type parameters, throws, and non-gate annotations");
                        valid = false;
                    }
                    for (JCVariableDecl parameter : method.params) {
                        if (parameter.name.toString().startsWith(PREFIX)) {
                            error(parameter, "Names beginning with " + PREFIX + " are reserved for generated feature gates");
                            valid = false;
                        }
                    }
                }
                if (fallback != null) {
                    fallbackValue = "default";
                    for (Map.Entry<String, JCMethodDecl> variant : variants.entrySet()) {
                        if (variant.getValue() == fallback && !variant.getKey().equals("default")) {
                            fallbackValue = variant.getKey();
                            break;
                        }
                    }
                }
                return valid;
            }

            private JCMethodDecl generate(int groupIndex) {
                JCMethodDecl wrapper = copier.copy(fallback);
                ListBuffer<JCAnnotation> annotations = new ListBuffer<>();
                for (JCAnnotation annotation : wrapper.mods.annotations) {
                    if (!isGate(annotation)) {
                        annotations.append(annotation);
                    }
                }
                wrapper.mods.annotations = annotations.toList();
                Map<JCMethodDecl, String> helpers = new LinkedHashMap<>();
                int index = 0;
                for (JCMethodDecl method : methods) {
                    String helper = PREFIX + "gate" + groupIndex + "$" + index++;
                    helpers.put(method, helper);
                    method.name = names.fromString(helper);
                    method.mods.flags = PRIVATE | (method.mods.flags & 2048L);
                    method.mods.annotations = List.nil();
                }
                StringBuilder body = new StringBuilder("{ String ").append(PREFIX).append("value = ")
                        .append("((").append(SUPPORT).append(")null).variation(this.").append(PREFIX).append("client,")
                        .append(quote(flag)).append(',').append(quote(fallbackValue)).append(");");
                for (Map.Entry<String, JCMethodDecl> variant : variants.entrySet()) {
                    if (variant.getValue() != fallback) {
                        body.append("if (").append(quote(variant.getKey())).append(".equals(").append(PREFIX)
                                .append("value)) {").append(invoke(wrapper, helpers.get(variant.getValue()))).append('}');
                    }
                }
                body.append(invoke(wrapper, helpers.get(fallback))).append('}');
                wrapper.body = parseBody(body.toString());
                return wrapper;
            }

            private String invoke(JCMethodDecl method, String helper) {
                boolean isVoid = method.restype.toString().equals("void");
                StringBuilder call = new StringBuilder(isVoid ? "" : "return ").append("this.").append(helper).append('(');
                boolean first = true;
                for (JCVariableDecl parameter : method.params) {
                    if (!first) {
                        call.append(',');
                    }
                    call.append(parameter.name);
                    first = false;
                }
                return call.append(");").append(isVoid ? "return;" : "").toString();
            }
        }
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': result.append("\\\\"); break;
                case '"': result.append("\\\""); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (character < 32 || character > 126) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
            }
        }
        return result.append('"').toString();
    }

    private static final class Gate {
        private final String flag;
        private final String value;
        private final JCAnnotation annotation;

        private Gate(String flag, String value, JCAnnotation annotation) {
            this.flag = flag;
            this.value = value;
            this.annotation = annotation;
        }
    }
}
