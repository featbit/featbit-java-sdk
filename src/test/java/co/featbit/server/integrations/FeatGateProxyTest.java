package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatGateProxyTest {
    private final FBClient client = createStrictMock(FBClient.class);
    private final FBUser user = new FBUser.Builder("user-1").userName("Albert").build();

    @AfterEach
    void clearUser() {
        FBUserContextHolder.remove();
    }

    @Test
    void routesIndependentFlagsAndReadsCurrentUserOnEachCall() {
        FBUser otherUser = new FBUser.Builder("user-2").userName("Marie").build();
        expect(client.variation("first-name", user, null)).andReturn("v2");
        expect(client.variation("last-name", user, null)).andReturn("false");
        expect(client.variation("first-name", otherUser, null)).andReturn("v1");
        expect(client.variation("last-name", otherUser, null)).andReturn("true");
        replay(client);

        Names names = FeatGateProxy.create(Names.class, new NameService(), client);
        FBUserContextHolder.setCurrentUser(user, false);
        assertEquals("ALBERT", names.firstName("albert"));
        assertEquals("einstein", names.lastName("einstein"));
        FBUserContextHolder.setCurrentUser(otherUser, false);
        assertEquals("marie", names.firstName("marie"));
        assertEquals("C.", names.lastName("curie"));
        verify(client);
    }

    @Test
    void reevaluatesRatherThanCachingTheVariation() {
        expect(client.variation("first-name", user, null)).andReturn("v2");
        expect(client.variation("first-name", user, null)).andReturn("v1");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        Names names = FeatGateProxy.create(Names.class, new NameService(), client);

        assertEquals("ALBERT", names.firstName("albert"));
        assertEquals("albert", names.firstName("albert"));
        verify(client);
    }

    @Test
    void fallsBackForAbsentUserAndUnmappedOrNullVariations() {
        expect(client.variation("first-name", user, null)).andReturn("unknown");
        expect(client.variation("first-name", user, null)).andReturn(null);
        replay(client);
        Names names = FeatGateProxy.create(Names.class, new NameService(), client);

        assertEquals("albert", names.firstName("albert"));
        FBUserContextHolder.setCurrentUser(user, false);
        assertEquals("albert", names.firstName("albert"));
        assertEquals("albert", names.firstName("albert"));
        verify(client);
    }

    @Test
    void passesThroughUnannotatedOverloadsWithoutEvaluation() {
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        Names names = FeatGateProxy.create(Names.class, new NameService(), client);

        assertEquals("plain", names.firstName());
        verify(client);
    }

    @Test
    void supportsInterfaceAnnotationsAndPrivateInheritedCovariantVariants() {
        expect(client.variation("number", user, null)).andReturn("double");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        Numbers numbers = FeatGateProxy.create(Numbers.class, new NumberService(), client);

        assertEquals(8, numbers.number(4));
        verify(client);
    }

    @Test
    void supportsInheritedImplementationAnnotations() {
        expect(client.variation("first-name", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        Names names = FeatGateProxy.create(Names.class, new InheritedNameService(), client);

        assertEquals("ALBERT", names.firstName("albert"));
        verify(client);
    }

    @Test
    void validatesReturnTypesAgainstTheServiceInterface() {
        expect(client.variation("number", user, null)).andReturn("fraction");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        Numbers numbers = FeatGateProxy.create(Numbers.class, new CovariantNumberService(), client);

        assertEquals(2.5, numbers.number(5));
        verify(client);
    }

    @Test
    void supportsDefaultInterfaceVariants() {
        expect(client.variation("number", user, null)).andReturn("double");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        DefaultNumbers numbers = FeatGateProxy.create(DefaultNumbers.class, new DefaultNumbers() {}, client);

        assertEquals(8, numbers.number(4));
        verify(client);
    }

    @Test
    void resolvesGenericImplementationBridgesWithoutSelectingOtherOverloads() {
        expect(client.variation("name", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        GenericName<String> names = FeatGateProxy.create(GenericName.class, new StringNames(), client);

        assertEquals("ALBERT", names.name("albert"));
        verify(client);
    }

    @Test
    void resolvesAnnotationsOnGenericInterfaces() {
        expect(client.variation("name", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        AnnotatedGenericName<String> names = FeatGateProxy.create(
                AnnotatedGenericName.class, new InterfaceAnnotatedStringNames(), client);

        assertEquals("ALBERT", names.name("albert"));
        verify(client);
    }

    @Test
    void resolvesInheritedGenericImplementationAndVariantMethods() {
        expect(client.variation("name", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        GenericName<String> names = FeatGateProxy.create(GenericName.class, new InheritedStringNames(), client);

        assertEquals("selected", names.name("albert"));
        verify(client);
    }

    @Test
    void rejectsVariantReturnsIncompatibleWithResolvedGenericContracts() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> FeatGateProxy.create(GenericName.class, new WrongStringNames(), client));

        assertTrue(failure.getMessage().startsWith("Incompatible variant method:"));
    }

    @Test
    void resolvesBridgesOnSpecializedChildInterfaces() {
        expect(client.variation("name", user, null)).andReturn("v2").times(2);
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        SpecializedName names = FeatGateProxy.create(SpecializedName.class, new SpecializedNames(), client);

        assertEquals("ALBERT", names.name("albert"));
        assertEquals("ALBERT", ((GenericName<String>) names).name("albert"));
        verify(client);
    }

    @Test
    void resolvesBridgesOnSpecializedDefaultMethods() {
        expect(client.variation("name", user, null)).andReturn("v2").times(2);
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        SpecializedDefaultName names = FeatGateProxy.create(
                SpecializedDefaultName.class, new SpecializedDefaultName() {}, client);

        assertEquals("ALBERT", names.name("albert"));
        assertEquals("ALBERT", ((GenericName<String>) names).name("albert"));
        verify(client);
    }

    @Test
    void routesAnnotationsOnDuplicateInheritedInterfaceMethods() {
        expect(client.variation("name", user, null)).andReturn("v2").times(3);
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        CombinedName names = FeatGateProxy.create(CombinedName.class, new CombinedNames(), client);

        assertEquals("ALBERT", names.name("albert"));
        assertEquals("ALBERT", ((PlainName) names).name("albert"));
        assertEquals("ALBERT", ((GatedName) names).name("albert"));
        verify(client);
    }

    @Test
    void acceptsEqualAnnotationsOnDuplicateInheritedMethods() {
        expect(client.variation("name", user, null)).andReturn("v2").times(2);
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        EqualCombinedName names = FeatGateProxy.create(
                EqualCombinedName.class, new EqualCombinedNames(), client);

        assertEquals("ALBERT", names.name("albert"));
        assertEquals("ALBERT", ((EquallyGatedName) names).name("albert"));
        verify(client);
    }

    @Test
    void rejectsConflictingAnnotationsOnDuplicateInheritedMethods() {
        assertThrows(IllegalArgumentException.class,
                () -> FeatGateProxy.create(ConflictingCombinedName.class, new ConflictingCombinedNames(), client));
    }

    @Test
    void rejectsIncompatibleParameterizedVariantReturns() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> FeatGateProxy.create(ListNames.class, new WrongListNames(), client));

        assertTrue(failure.getMessage().startsWith("Incompatible variant method:"));
    }

    @Test
    void acceptsCompatibleParameterizedReturnSubtypes() {
        expect(client.variation("names", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        ListNames names = FeatGateProxy.create(ListNames.class, new ArrayListNames(), client);

        assertEquals("ALBERT", names.names().get(0));
        verify(client);
    }

    @Test
    void acceptsVariantReturnsWithinWildcardBounds() {
        expect(client.variation("numbers", user, null)).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        WildcardNumbers numbers = FeatGateProxy.create(WildcardNumbers.class, new WildcardNumberService(), client);

        assertEquals(2, numbers.numbers().get(0).intValue());
        verify(client);
    }

    @Test
    void routesMultipleVariantsWithPrimitiveArgumentsAndVoidReturns() {
        expect(client.variation("counter", user, null)).andReturn("double");
        expect(client.variation("counter", user, null)).andReturn("triple");
        expect(client.variation("counter", user, null)).andReturn(null);
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        CounterService target = new CounterService();
        Counter counter = FeatGateProxy.create(Counter.class, target, client);

        counter.add(2);
        counter.add(3);
        counter.add(1);
        assertEquals(14, target.total);
        verify(client);
    }

    @Test
    void preservesCheckedVariantExceptionsWithoutCallingFallback() {
        expect(client.variation("failure", user, null)).andReturn("new");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        FailingService target = new FailingService();
        Failing proxy = FeatGateProxy.create(Failing.class, target, client);

        assertSame(target.failure, assertThrows(IOException.class, proxy::run));
        assertEquals(0, target.fallbackCalls);
        verify(client);
    }

    @Test
    void preservesFallbackExceptions() {
        replay(client);
        FailingService target = new FailingService();
        Failing proxy = FeatGateProxy.create(Failing.class, target, client);

        assertSame(target.failure, assertThrows(IOException.class, proxy::run));
        assertEquals(1, target.fallbackCalls);
        verify(client);
    }

    @Test
    void usesIdentityForObjectMethodsWithoutEvaluation() {
        replay(client);
        NameService target = new NameService();
        Names proxy = FeatGateProxy.create(Names.class, target, client);
        Names other = FeatGateProxy.create(Names.class, target, client);

        assertEquals(proxy, proxy);
        assertNotEquals(proxy, other);
        assertNotEquals(proxy, target);
        assertFalse(proxy.equals(null));
        assertEquals(System.identityHashCode(proxy), proxy.hashCode());
        assertEquals(target.toString(), proxy.toString());
        verify(client);
    }

    @Test
    void rejectsInvalidMappingsAtCreation() {
        Name[] invalid = {new BlankFlag(), new DuplicateValues(), new MissingMethod(),
                new WrongReturn(), new StaticVariant(), new OriginalAsVariant()};

        for (Name target : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> FeatGateProxy.create(Name.class, target, client), target.getClass().getSimpleName());
        }
    }

    @Test
    void requiresAnInterface() {
        assertThrows(IllegalArgumentException.class,
                () -> FeatGateProxy.create(NameService.class, new NameService(), client));
    }

    public interface Names {
        String firstName(String name);
        String firstName();
        String lastName(String name);
    }

    public static class NameService implements Names {
        @FeatGate(flag = "first-name", variants = @FeatGate.Variant(value = "v2", method = "upperCase"))
        public String firstName(String name) {
            return name;
        }

        public String firstName() {
            return "plain";
        }

        @FeatGate(flag = "last-name", variants = @FeatGate.Variant(value = "true", method = "initial"))
        public String lastName(String name) {
            return name;
        }

        private String upperCase(String name) {
            return name.toUpperCase(java.util.Locale.ROOT);
        }

        private String upperCase() {
            return "wrong overload";
        }

        private String initial(String name) {
            return Character.toUpperCase(name.charAt(0)) + ".";
        }
    }

    public static class InheritedNameService extends NameService {
    }

    public interface Numbers {
        @FeatGate(flag = "number", variants = @FeatGate.Variant(value = "double", method = "doubled"))
        Number number(int value);
    }

    public static class NumberVariants {
        private Integer doubled(int value) {
            return value * 2;
        }
    }

    public static class NumberService extends NumberVariants implements Numbers {
        public Number number(int value) {
            return value;
        }
    }

    public static class CovariantNumberService implements Numbers {
        @FeatGate(flag = "number", variants = @FeatGate.Variant(value = "fraction", method = "fraction"))
        public Integer number(int value) {
            return value;
        }

        private Double fraction(int value) {
            return value / 2.0;
        }
    }

    public interface DefaultNumbers {
        @FeatGate(flag = "number", variants = @FeatGate.Variant(value = "double", method = "doubled"))
        default int number(int value) {
            return value;
        }

        default int doubled(int value) {
            return value * 2;
        }
    }

    public interface Counter {
        void add(int value);
    }

    public interface GenericName<T> {
        T name(T value);
    }

    public static class StringNames implements GenericName<String> {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public String name(String value) {
            return value;
        }

        private String alternate(String value) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }

        private String alternate(Integer value) {
            return "wrong overload";
        }
    }

    public interface AnnotatedGenericName<T> {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        T name(T value);
    }

    public static class InterfaceAnnotatedStringNames implements AnnotatedGenericName<String> {
        public String name(String value) {
            return value;
        }

        private String alternate(String value) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }
    }

    public static class GenericNames<T> implements GenericName<T> {
        private final T selected;

        GenericNames(T selected) {
            this.selected = selected;
        }

        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public T name(T value) {
            return value;
        }

        private T alternate(T value) {
            return selected;
        }
    }

    public static class InheritedStringNames extends GenericNames<String> {
        InheritedStringNames() {
            super("selected");
        }
    }

    public static class WrongGenericNames<T> implements GenericName<T> {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public T name(T value) {
            return value;
        }

        private Integer alternate(T value) {
            return 1;
        }
    }

    public static class WrongStringNames extends WrongGenericNames<String> {
    }

    public interface SpecializedName extends GenericName<String> {
        String name(String value);
    }

    public static class SpecializedNames extends StringNames implements SpecializedName {
    }

    public interface SpecializedDefaultName extends GenericName<String> {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        default String name(String value) {
            return value;
        }

        default String alternate(String value) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }
    }

    public interface PlainName {
        String name(String value);
    }

    public interface GatedName {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        String name(String value);
    }

    public interface CombinedName extends PlainName, GatedName {
    }

    public static class CombinedNames implements CombinedName {
        public String name(String value) {
            return value;
        }

        private String alternate(String value) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }
    }

    public interface EquallyGatedName {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        String name(String value);
    }

    public interface EqualCombinedName extends GatedName, EquallyGatedName {
    }

    public static class EqualCombinedNames extends CombinedNames implements EqualCombinedName {
    }

    public interface ConflictingGatedName {
        @FeatGate(flag = "other-name", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        String name(String value);
    }

    public interface ConflictingCombinedName extends GatedName, ConflictingGatedName {
    }

    public static class ConflictingCombinedNames extends CombinedNames implements ConflictingCombinedName {
    }

    public interface ListNames {
        List<String> names();
    }

    public static class WrongListNames implements ListNames {
        @FeatGate(flag = "names", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public List<String> names() { return Arrays.asList("albert"); }
        private List<Integer> alternate() { return Arrays.asList(1); }
    }

    public static class ArrayListNames implements ListNames {
        @FeatGate(flag = "names", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public List<String> names() { return Arrays.asList("albert"); }
        private ArrayList<String> alternate() { return new ArrayList<>(Arrays.asList("ALBERT")); }
    }

    public interface WildcardNumbers {
        List<? extends Number> numbers();
    }

    public static class WildcardNumberService implements WildcardNumbers {
        @FeatGate(flag = "numbers", variants = @FeatGate.Variant(value = "v2", method = "alternate"))
        public List<? extends Number> numbers() { return Arrays.asList(1); }
        private List<Integer> alternate() { return Arrays.asList(2); }
    }

    public static class CounterService implements Counter {
        int total;

        @FeatGate(flag = "counter", variants = {
                @FeatGate.Variant(value = "double", method = "addDouble"),
                @FeatGate.Variant(value = "triple", method = "addTriple")})
        public void add(int value) {
            total += value;
        }

        private void addDouble(int value) {
            total += value * 2;
        }

        private void addTriple(int value) {
            total += value * 3;
        }
    }

    public interface Failing {
        String run() throws IOException;
    }

    public static class FailingService implements Failing {
        final IOException failure = new IOException("business failure");
        int fallbackCalls;

        @FeatGate(flag = "failure", variants = @FeatGate.Variant(value = "new", method = "fail"))
        public String run() throws IOException {
            fallbackCalls++;
            throw failure;
        }

        private String fail() throws IOException {
            throw failure;
        }
    }

    public interface Name {
        String name();
    }

    public static class BlankFlag implements Name {
        @FeatGate(flag = " ", variants = {})
        public String name() { return "name"; }
    }

    public static class DuplicateValues implements Name {
        @FeatGate(flag = "name", variants = {
                @FeatGate.Variant(value = "v2", method = "other"),
                @FeatGate.Variant(value = "v2", method = "other")})
        public String name() { return "name"; }
        private String other() { return "other"; }
    }

    public static class MissingMethod implements Name {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "missing"))
        public String name() { return "name"; }
    }

    public static class WrongReturn implements Name {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "other"))
        public String name() { return "name"; }
        private Integer other() { return 1; }
    }

    public static class StaticVariant implements Name {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "other"))
        public String name() { return "name"; }
        private static String other() { return "other"; }
    }

    public static class OriginalAsVariant implements Name {
        @FeatGate(flag = "name", variants = @FeatGate.Variant(value = "v2", method = "name"))
        public String name() { return "name"; }
    }
}
