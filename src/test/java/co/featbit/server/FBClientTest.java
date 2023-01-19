package co.featbit.server;

import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import co.featbit.commons.model.FlagState;
import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import co.featbit.server.exterior.DataSynchronizer;
import co.featbit.server.exterior.FBClient;
import co.featbit.server.exterior.InsightProcessor;
import org.easymock.EasyMockExtension;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.featbit.server.Evaluator.REASON_CLIENT_NOT_READY;
import static co.featbit.server.Evaluator.REASON_ERROR;
import static co.featbit.server.Evaluator.REASON_FALLTHROUGH;
import static co.featbit.server.Evaluator.REASON_FLAG_NOT_FOUND;
import static co.featbit.server.Evaluator.REASON_RULE_MATCH;
import static co.featbit.server.Evaluator.REASON_TARGET_MATCH;
import static co.featbit.server.Evaluator.REASON_USER_NOT_SPECIFIED;
import static co.featbit.server.Evaluator.REASON_WRONG_TYPE;
import static co.featbit.server.TestFactory.mockDataStorageFactory;
import static co.featbit.server.TestFactory.mockDataSynchronizerFactory;
import static co.featbit.server.TestFactory.mockInsightProcessorFactory;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(EasyMockExtension.class)
class FBClientTest extends FBClientBaseTest {
    private FBUser user1;
    private FBUser user2;
    private FBUser user3;
    private FBUser user4;
    private FBUser cnPhoneNumber;
    private FBUser frPhoneNumber;
    private FBUser email;

    private FBConfig.Builder fakeConfigBuilder;

    private final EasyMockSupport support = new EasyMockSupport();

    Future<Boolean> initFuture;

    private DataSynchronizer dataSynchronizer;

    private InsightProcessor insightProcessor;

    private DataStorage dataStorage;

    @BeforeEach
    void init() {
        user1 = new FBUser.Builder("test-user-1").userName("test-user-1").custom("country", "us").build();
        user2 = new FBUser.Builder("test-user-2").userName("test-user-2").custom("country", "fr").build();
        user3 = new FBUser.Builder("test-user-3").userName("test-user-3").custom("country", "cn").custom("major", "cs").build();
        user4 = new FBUser.Builder("test-user-4").userName("test-user-4").custom("country", "uk").custom("major", "physics").build();
        cnPhoneNumber = new FBUser.Builder("18555358000").userName("test-user-5").build();
        frPhoneNumber = new FBUser.Builder("0603111111").userName("test-user-6").build();
        email = new FBUser.Builder("test-user-7@featbit.com").userName("test-user-7").build();
        fakeConfigBuilder = new FBConfig.Builder()
                .streamingURL(fakeUrl)
                .eventURL(fakeUrl);
        initFuture = support.createNiceMock(Future.class);
        dataSynchronizer = support.createNiceMock(DataSynchronizer.class);
        insightProcessor = support.createNiceMock(InsightProcessor.class);
        dataStorage = support.createNiceMock(DataStorage.class);
    }

    private FBClient createMockClient(FBConfig.Builder config) {
        config.dataSynchronizerFactory(mockDataSynchronizerFactory(dataSynchronizer))
                .insightProcessorFactory(mockInsightProcessorFactory(insightProcessor))
                .dataStorageFactory(mockDataStorageFactory(dataStorage));
        return new FBClientImp(fakeEnvSecret, config.build());
    }

    @Test
    void testBoolVariation() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            //logic tests
            //user1 and user2 in the targeting users lists should return true
            boolean res = client.isEnabled("ff-test-bool", user1);
            assertTrue(res);
            FlagState<Boolean> state = client.boolVariationDetail("ff-test-bool", user2, false);
            assertTrue(state.getData().getVariation());
            assertEquals(REASON_TARGET_MATCH, state.getData().getReason());
            //user3 and user4 should get true or false according to percentage rollout
            res = client.isEnabled("ff-test-bool", user3);
            assertFalse(res);
            state = client.boolVariationDetail("ff-test-bool", user4, false);
            assertTrue(state.getData().getVariation());
            assertEquals(REASON_FALLTHROUGH, state.getData().getReason());
        }
    }

    @Test
    void testNumericVariation() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            //get country numbers according to user's country
            // us=1, fr=33, cn=86, others=9999
            int res = client.intVariation("ff-test-number", user1, -1);
            assertEquals(1, res);
            FlagState<Long> state = client.longVariationDetail("ff-test-number", user2, -1L);
            assertEquals(33L, state.getData().getVariation());
            assertEquals(REASON_RULE_MATCH, state.getData().getReason());
            double res1 = client.doubleVariation("ff-test-number", user3, -1D);
            assertEquals(86D, res1);
            FlagState<Double> state1 = client.doubleVariationDetail("ff-test-number", user4, -1D);
            assertEquals(9999D, state1.getData().getVariation());
            assertEquals(REASON_FALLTHROUGH, state1.getData().getReason());
        }
    }

    @Test
    void testStringVariation() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            // get nature of user key
            String res = client.variation("ff-test-string", cnPhoneNumber, "error");
            assertEquals("phone number", res);
            res = client.variation("ff-test-string", frPhoneNumber, "error");
            assertEquals("phone number", res);
            res = client.variation("ff-test-string", email, "error");
            assertEquals("email", res);
            FlagState<String> state = client.variationDetail("ff-test-string", user1, "error");
            assertEquals("others", state.getData().getVariation());
            assertEquals(REASON_FALLTHROUGH, state.getData().getReason());
        }
    }

    @Test
    void testSegment() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            // teamA rules: user in seg Including users list or user's major is one of cs or math
            String res = client.variation("ff-test-seg", user1, "error");
            assertEquals("teamA", res);
            res = client.variation("ff-test-seg", user2, "error");
            assertEquals("teamB", res);
            res = client.variation("ff-test-seg", user3, "error");
            assertEquals("teamA", res);
            res = client.variation("ff-test-seg", user4, "error");
            assertEquals("teamB", res);
        }
    }

    @Test
    void testJsonVariation() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            //dummy game: 25% win 100 euros
            Dummy dummy1 = client.jsonVariation("ff-test-json", user1, Dummy.class, null);
            assertEquals(200, dummy1.code);
            FlagState<Dummy> dummy2 = client.jsonVariationDetail("ff-test-json", user2, Dummy.class, null);
            assertEquals(404, dummy2.getData().getVariation().code);
            assertEquals(REASON_FALLTHROUGH, dummy2.getData().getReason());
        }
    }

    @Test
    void testFlagKnown() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            assertTrue(client.isFlagKnown("ff-test-bool"));
            assertTrue(client.isFlagKnown("ff-test-number"));
            assertTrue(client.isFlagKnown("ff-test-string"));
            assertTrue(client.isFlagKnown("ff-test-json"));
            assertTrue(client.isFlagKnown("ff-test-seg"));
            assertFalse(client.isFlagKnown("ff-not-existed"));
        }
    }

    @Test
    void testAllLatestFlagsVariations() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            AllFlagStates states = client.getAllLatestFlagsVariations(user1);
            EvalDetail<Boolean> ed1 = states.getBooleanDetail("ff-test-bool", false);
            assertEquals(true, ed1.getVariation());
            assertEquals(REASON_TARGET_MATCH, ed1.getReason());
            EvalDetail<Integer> ed2 = states.getIntegerDetail("ff-test-number", 0);
            assertEquals(1, ed2.getVariation());
            assertEquals(REASON_RULE_MATCH, ed2.getReason());
            EvalDetail<String> ed3 = states.getStringDetail("ff-test-string", "");
            assertEquals("others", ed3.getVariation());
            assertEquals(REASON_FALLTHROUGH, ed3.getReason());
            String team = states.getString("ff-test-seg", "");
            assertEquals("teamA", team);
        }
    }

    @Test
    void testVariationArgumentError() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            FlagState<Boolean> state = client.boolVariationDetail("", user1, false);
            assertFalse(state.getData().getVariation());
            assertEquals(REASON_FLAG_NOT_FOUND, state.getData().getReason());
            state = client.boolVariationDetail("ff-not-existed", user1, false);
            assertFalse(state.getData().getVariation());
            assertEquals(REASON_FLAG_NOT_FOUND, state.getData().getReason());
            state = client.boolVariationDetail("ff-test-bool", null, false);
            assertFalse(state.getData().getVariation());
            assertEquals(REASON_USER_NOT_SPECIFIED, state.getData().getReason());
            FlagState<Integer> state1 = client.intVariationDetail("ff-test-bool", user1, -1);
            assertEquals(-1, state1.getData().getVariation());
            assertEquals(REASON_WRONG_TYPE, state1.getData().getReason());
            AllFlagStates states = client.getAllLatestFlagsVariations(null);
            assertFalse(states.isSuccess());
            assertEquals(REASON_USER_NOT_SPECIFIED, states.getMessage());
        }
    }

    @Test
    void testVariationWhenClientNotInitialized() throws Exception {
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(false);
        expect(dataSynchronizer.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ofMillis(10));
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            assertFalse(client.isInitialized());
            assertFalse(client.isFlagKnown("ff-test-bool"));
            FlagState<Boolean> state = client.boolVariationDetail("ff-test-bool", user1, false);
            assertFalse(state.getData().getVariation());
            assertEquals(REASON_CLIENT_NOT_READY, state.getData().getReason());
            AllFlagStates states = client.getAllLatestFlagsVariations(user1);
            assertFalse(states.isSuccess());
            assertEquals(REASON_CLIENT_NOT_READY, states.getMessage());
            support.verifyAll();
        }
    }

    @Test
    void testVariationThrowException() throws Exception {
        expect(dataStorage.get(anyObject(DataStorageTypes.Category.class), anyString())).andThrow(new RuntimeException("test exception"));
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(true);
        expect(dataSynchronizer.isInitialized()).andReturn(true).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ofMillis(10));
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            FlagState<Boolean> state = client.boolVariationDetail("ff-test-bool", user1, false);
            assertFalse(state.getData().getVariation());
            assertEquals(REASON_ERROR, state.getData().getReason());
            support.verifyAll();
        }
    }

    @Test
    void testConstructNullConfig() throws IOException {
        try (FBClient client = new FBClientImp("fake", null)) {
            fail("null pointer exception");
        } catch (NullPointerException e) {
            assertEquals("FBConfig Should not be null", e.getMessage());
        }
    }

    @Test
    void testConstructEmptyEnvSecret() throws IOException {
        try (FBClient client = new FBClientImp("", fakeConfigBuilder.build())) {
            fail("illegal argument exception");
        } catch (IllegalArgumentException e) {
            assertEquals("envSecret is invalid", e.getMessage());
        }
    }

    @Test
    void testConstructIllegalEnvSecret() throws IOException {
        try (FBClient client = new FBClientImp(fakeEnvSecret + "©öäü£", fakeConfigBuilder.build())) {
            fail("illegal argument exception");
        } catch (IllegalArgumentException e) {
            assertEquals("envSecret is invalid", e.getMessage());
        }
    }

    @Test
    void testConstructEmptyUrl() throws IOException {
        FBConfig errorConfig = new FBConfig.Builder()
                .streamingURL("")
                .eventURL("")
                .build();
        try (FBClient client = new FBClientImp(fakeEnvSecret, errorConfig)) {
            fail("illegal argument exception");
        } catch (IllegalArgumentException e) {
            assertEquals("streaming or event url is invalid", e.getMessage());
        }
    }

    @Test
    void testConstructIllegalUrl() throws IOException {
        FBConfig errorConfig = new FBConfig.Builder()
                .streamingURL("urn:isbn:0-294-56559-3")
                .eventURL("mailto:John.Doe@example.com")
                .build();
        try (FBClient client = new FBClientImp(fakeEnvSecret, errorConfig)) {
            fail("illegal argument exception");
        } catch (IllegalArgumentException e) {
            assertEquals("streaming or event url is invalid", e.getMessage());
        }
    }

    @Test
    void testConstructStartWait() throws Exception {
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andReturn(true);
        expect(dataSynchronizer.isInitialized()).andReturn(true).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ofMillis(10));
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            assertTrue(client.isInitialized());
            support.verifyAll();
        }
    }

    @Test
    void testConstructStartNoWait() throws IOException {
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(dataSynchronizer.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ZERO);
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            assertFalse(client.isInitialized());
            support.verifyAll();
        }
    }

    @Test
    void testConstructStartThrowTimeoutException() throws Exception {
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new TimeoutException("test exception"));
        expect(dataSynchronizer.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ofMillis(10));
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            assertFalse(client.isInitialized());
            support.verifyAll();
        }
    }

    @Test
    void testConstructStartThrowException() throws Exception {
        expect(dataSynchronizer.start()).andReturn(initFuture);
        expect(initFuture.get(10L, TimeUnit.MILLISECONDS)).andThrow(new InterruptedException("test exception"));
        expect(dataSynchronizer.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();
        fakeConfigBuilder.startWaitTime(Duration.ofMillis(10));
        try (FBClient client = createMockClient(fakeConfigBuilder)) {
            assertFalse(client.isInitialized());
            support.verifyAll();
        }
    }

}
