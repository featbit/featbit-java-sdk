package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.DataStorageTypes;
import co.featbit.server.exterior.InsightProcessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InsightsEnabledTest extends FBClientBaseTest {
    private static final String KEY = "ff-test-bool";
    private final FBUser user = new FBUser.Builder("test-user-1").userName("test-user-1").build();

    private DataModel.Data data(Boolean enabled) throws Exception {
        JsonObject envelope = JsonParser.parseString(readResource("fbclient_test_data.json")).getAsJsonObject();
        for (JsonElement element : envelope.getAsJsonObject("data").getAsJsonArray("featureFlags")) {
            if (enabled != null && KEY.equals(element.getAsJsonObject().get("key").getAsString())) {
                element.getAsJsonObject().addProperty("insightsEnabled", enabled);
            }
        }
        return JsonHelper.deserialize(envelope.toString(), DataModel.All.class).data();
    }

    private FBClientImp client(InMemoryDataStorage storage, RecordingProcessor processor) {
        return new FBClientImp(fakeEnvSecret, new FBConfig.Builder()
                .streamingURL("ws://fake-url").eventURL("http://fake-url")
                .startWaitTime(Duration.ZERO)
                .dataStorageFactory(context -> storage)
                .dataSynchronizerFactory(Factory.externalDataSynchronization())
                .insightProcessorFactory(context -> processor).build());
    }

    @Test
    void singleAndBulkReadsRespectInsightsWithoutChangingResults() throws Exception {
        for (Boolean enabled : new Boolean[]{null, true, false}) {
            DataModel.Data data = data(enabled);
            InMemoryDataStorage storage = new InMemoryDataStorage();
            storage.init(data.toStorageType(), data.getTimestamp());
            RecordingProcessor processor = new RecordingProcessor();
            try (FBClientImp client = client(storage, processor)) {
                EvalDetail<Boolean> detail = client.boolVariationDetail(KEY, user, false);
                assertTrue(detail.getVariation());
                assertEquals(Evaluator.REASON_TARGET_MATCH, detail.getReason());
                assertEquals(Boolean.FALSE.equals(enabled) ? 0 : 1, processor.events.size());
                processor.events.clear();

                AllFlagStates states = client.getAllLatestFlagsVariations(user);
                assertTrue(states.isSuccess());
                assertEquals(0, processor.events.size());
                detail = states.getBooleanDetail(KEY, false);
                assertTrue(detail.getVariation());
                assertEquals(Evaluator.REASON_TARGET_MATCH, detail.getReason());
                assertEquals(Boolean.FALSE.equals(enabled) ? 0 : 1, processor.events.size());
            }
        }
    }

    @Test
    void bulkReadsApplyInsightsPerFlag() throws Exception {
        DataModel.Data data = data(false);
        InMemoryDataStorage storage = new InMemoryDataStorage();
        storage.init(data.toStorageType(), data.getTimestamp());
        RecordingProcessor processor = new RecordingProcessor();
        try (FBClientImp client = client(storage, processor)) {
            AllFlagStates states = client.getAllLatestFlagsVariations(user);
            assertTrue(states.getBoolean(KEY, false));
            assertEquals(0, processor.events.size());
            assertEquals("others", states.getString("ff-test-string", "fallback"));
            assertEquals(1, processor.events.size());
        }
    }

    @Test
    void exportedDataPreservesExplicitFalse() throws Exception {
        DataModel.Data data = data(false);
        InMemoryDataStorage storage = new InMemoryDataStorage();
        storage.init(data.toStorageType(), data.getTimestamp());
        try (FBClientImp client = client(storage, new RecordingProcessor())) {
            DataModel.DataExposer exported = JsonHelper.deserialize(client.getAllDataAsJson(), DataModel.DataExposer.class);
            assertFalse(exported.getFeatureFlags().stream().filter(flag -> KEY.equals(flag.getKey()))
                    .findFirst().get().isInsightsEnabled());
        }
    }

    @Test
    void updatesToggleCollectionAndBulkResultsRetainTheirSnapshot() throws Exception {
        DataModel.Data data = data(false);
        InMemoryDataStorage storage = new InMemoryDataStorage();
        storage.init(data.toStorageType(), data.getTimestamp());
        RecordingProcessor processor = new RecordingProcessor();
        try (FBClientImp client = client(storage, processor)) {
            AllFlagStates disabledSnapshot = client.getAllLatestFlagsVariations(user);
            DataModel.FeatureFlag flag = data(true).getFeatureFlags().stream()
                    .filter(item -> KEY.equals(item.getKey())).findFirst().get();
            flag.updatedAt.setTime(storage.getVersion() + 1);
            flag.afterDeserialization();
            assertTrue(storage.upsert(DataStorageTypes.FEATURES, KEY, flag, flag.getTimestamp()));
            assertTrue(client.boolVariation(KEY, user, false));
            assertTrue(client.getAllLatestFlagsVariations(user).getBoolean(KEY, false));
            assertEquals(2, processor.events.size());
            processor.events.clear();
            assertTrue(disabledSnapshot.getBoolean(KEY, false));
            assertEquals(0, processor.events.size());
        }
    }

    private static class RecordingProcessor implements InsightProcessor {
        final List<InsightTypes.Event> events = new ArrayList<>();

        public void send(InsightTypes.Event event) { events.add(event); }
        public void flush() { }
        public void close() { }
    }
}
