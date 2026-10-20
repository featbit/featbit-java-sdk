package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FBClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatGateSupportTest {
    private final FBClient client = createStrictMock(FBClient.class);
    private final FBUser user = user("user-1");

    @AfterEach
    void clearUser() {
        FBUserContextHolder.remove();
    }

    @Test
    void bindsPrivateFieldsThroughoutTheHierarchyWithoutUsingTheClient() {
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);
        ChildTarget target = new ChildTarget();

        assertSame(target, FeatGateSupport.bind(target, client));
        assertSame(client, target.client);
        assertSame(client, ((Target) target).client);
        verify(client);
    }

    @Test
    void keepsClientsAndTargetsIndependent() {
        FBClient otherClient = createStrictMock(FBClient.class);
        expect(client.variation("name", user, "v1")).andReturn("v2");
        expect(otherClient.variation("name", user, "v1")).andReturn("v3");
        replay(client, otherClient);
        Target first = FeatGateSupport.bind(new Target(), client);
        Target second = FeatGateSupport.bind(new Target(), otherClient);
        FBUserContextHolder.setCurrentUser(user, false);

        assertEquals("v2", first.variation());
        assertEquals("v3", second.variation());
        verify(client, otherClient);
    }

    @Test
    void rebindingDoesNotCloseOrEvaluateEitherClient() {
        FBClient replacement = createStrictMock(FBClient.class);
        replay(client, replacement);
        Target target = FeatGateSupport.bind(new Target(), client);

        assertSame(target, FeatGateSupport.bind(target, replacement));
        assertSame(replacement, target.client);
        verify(client, replacement);
    }

    @Test
    void bindingRemainsHarmlessAfterTheGatesAreDeleted() {
        replay(client);
        UngatedTarget target = new UngatedTarget();

        assertSame(target, FeatGateSupport.bind(target, client));
        assertNull(target.client);
        assertEquals("plain", target.name());
        verify(client);
    }

    @Test
    void rejectsNullBindingArguments() {
        replay(client);

        assertThrows(NullPointerException.class, () -> FeatGateSupport.bind(null, client));
        assertThrows(NullPointerException.class, () -> FeatGateSupport.bind(new Target(), null));
        verify(client);
    }

    @Test
    void usesTheDefaultWithoutAClientOrCurrentUser() {
        replay(client);
        Target target = FeatGateSupport.bind(new Target(), client);

        assertEquals("v1", target.variation());
        FBUserContextHolder.setCurrentUser(user, false);
        assertEquals("v1", new Target().variation());
        assertNull(FeatGateSupport.variation(null, "name", null));
        verify(client);
    }

    @Test
    void readsTheCurrentUserAndVariationOnEveryCall() {
        FBUser otherUser = user("user-2");
        expect(client.variation("name", user, "v1")).andReturn("v2");
        expect(client.variation("name", user, "v1")).andReturn("v1");
        expect(client.variation("name", otherUser, "v1")).andReturn("v3");
        replay(client);
        Target target = FeatGateSupport.bind(new Target(), client);
        FBUserContextHolder.setCurrentUser(user, false);

        assertEquals("v2", target.variation());
        assertEquals("v1", target.variation());
        FBUserContextHolder.setCurrentUser(otherUser, false);
        assertEquals("v3", target.variation());
        FBUserContextHolder.remove();
        assertEquals("v1", target.variation());
        verify(client);
    }

    @Test
    void usesInheritedUserContext() {
        expect(client.variation("name", user, "v1")).andReturn("v2");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, true);

        assertEquals("v2", FeatGateSupport.variation(client, "name", "v1"));
        verify(client);
    }

    @Test
    void preservesTheSdkResultAndPassesItsDefaultUnchanged() {
        expect(client.variation("missing", user, "fallback")).andReturn("fallback");
        expect(client.variation("nullable", user, null)).andReturn(null);
        expect(client.variation("unknown", user, "v1")).andReturn("not-mapped");
        replay(client);
        FBUserContextHolder.setCurrentUser(user, false);

        assertEquals("fallback", FeatGateSupport.variation(client, "missing", "fallback"));
        assertNull(FeatGateSupport.variation(client, "nullable", null));
        assertEquals("not-mapped", FeatGateSupport.variation(client, "unknown", "v1"));
        verify(client);
    }

    @Test
    void propagatesEvaluationFailuresWithoutRetryingOrFallingBack() {
        IllegalStateException failure = new IllegalStateException("evaluation failed");
        expect(client.variation("name", user, "v1")).andThrow(failure);
        replay(client);
        Target target = FeatGateSupport.bind(new Target(), client);
        FBUserContextHolder.setCurrentUser(user, false);

        assertSame(failure, assertThrows(IllegalStateException.class, target::variation));
        verify(client);
    }

    @Test
    void rejectsInvalidFieldsBeforeChangingAnyBinding() {
        FBClient previous = createStrictMock(FBClient.class);
        replay(client, previous);
        ValidThenInvalid target = new ValidThenInvalid();
        target.client = previous;

        assertThrows(IllegalArgumentException.class, () -> FeatGateSupport.bind(target, client));
        assertSame(previous, target.client);
        assertThrows(IllegalArgumentException.class, () -> FeatGateSupport.bind(new StaticField(), client));
        assertThrows(IllegalArgumentException.class, () -> FeatGateSupport.bind(new WrongTypeField(), client));
        verify(client, previous);
    }

    @Test
    void keepsConcurrentUsersIsolatedOnOneBoundTargetAndReusedWorkers() throws Exception {
        int workers = 4;
        int iterations = 200;
        expect(client.variation(eq("name"), isA(FBUser.class), eq("v1"))).andAnswer(() -> {
            FBUser evaluatedUser = getCurrentArgument(1);
            assertSame(evaluatedUser, FBUserContextHolder.getCurrentUser());
            return evaluatedUser.getKey();
        }).times(workers * iterations);
        makeThreadSafe(client, true);
        replay(client);
        Target target = FeatGateSupport.bind(new Target(), client);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                int index = worker;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        FBUserContextHolder.remove();
                        assertEquals("v1", target.variation());
                        FBUser current = user(index + "-" + iteration);
                        try {
                            FBUserContextHolder.setCurrentUser(current, iteration % 2 == 0);
                            assertEquals(current.getKey(), target.variation());
                        } finally {
                            FBUserContextHolder.remove();
                        }
                        assertNull(FBUserContextHolder.getCurrentUser());
                        assertEquals("v1", target.variation());
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> result : results) {
                result.get(15, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertNull(FBUserContextHolder.getCurrentUser());
        verify(client);
    }

    private static FBUser user(String key) {
        return new FBUser.Builder(key).userName(key).build();
    }

    private static class Target {
        @FeatGateSupport.Client
        private volatile FBClient client;

        String variation() {
            return FeatGateSupport.variation(client, "name", "v1");
        }
    }

    private static final class ChildTarget extends Target {
        @FeatGateSupport.Client
        private volatile FBClient client;
    }

    private static final class UngatedTarget {
        private FBClient client;

        String name() {
            return "plain";
        }
    }

    private static class InvalidBase {
        @FeatGateSupport.Client
        private FBClient client;
    }

    private static final class ValidThenInvalid extends InvalidBase {
        @FeatGateSupport.Client
        private volatile FBClient client;
    }

    private static final class StaticField {
        @FeatGateSupport.Client
        private static volatile FBClient client;
    }

    private static final class WrongTypeField {
        @FeatGateSupport.Client
        private volatile Object client;
    }
}
