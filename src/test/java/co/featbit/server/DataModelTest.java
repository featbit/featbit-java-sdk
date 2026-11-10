package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.server.exterior.DataStorageTypes;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DataModelTest {

    @Test
    void toStorageTypeKeepsLiveFlagWhenItFollowsArchivedCopyWithSameKey() throws Exception {
        // featbit-agent order: archived copy first, re-created flag last
        JsonObject envelope = envelope();
        JsonArray flags = featureFlags(envelope);
        JsonObject live = flags.get(0).getAsJsonObject();
        flags.set(0, copyWithNewId(live, true));
        flags.add(live);
        String key = live.get("key").getAsString();

        Map<String, DataStorageTypes.Item> items =
                assertDoesNotThrow(data(envelope)::toStorageType).get(DataStorageTypes.FEATURES);

        assertFalse(items.get(key).isArchived());
    }

    @Test
    void toStorageTypeKeepsLastEntryWhenTwoArchivedCopiesHaveSameKey() throws Exception {
        JsonObject envelope = envelope();
        JsonArray flags = featureFlags(envelope);
        JsonObject first = flags.get(0).getAsJsonObject();
        first.addProperty("isArchived", true);
        JsonObject last = copyWithNewId(first, true);
        last.addProperty("updatedAt", "2030-01-01T00:00:00.000Z");
        flags.add(last);
        String key = first.get("key").getAsString();

        DataModel.Data data = data(envelope);
        long lastTimestamp = data.getFeatureFlags().get(flags.size() - 1).getTimestamp();
        Map<String, DataStorageTypes.Item> items =
                assertDoesNotThrow(data::toStorageType).get(DataStorageTypes.FEATURES);

        assertTrue(items.get(key).isArchived());
        assertEquals(lastTimestamp, items.get(key).getTimestamp().longValue());
    }

    @Test
    void toStorageTypeKeepsFlagsWithDistinctKeys() throws Exception {
        JsonObject envelope = envelope();
        int count = featureFlags(envelope).size();

        assertEquals(count, data(envelope).toStorageType().get(DataStorageTypes.FEATURES).size());
    }

    private static JsonObject copyWithNewId(JsonObject flag, boolean archived) {
        JsonObject copy = flag.deepCopy();
        copy.addProperty("id", UUID.randomUUID().toString());
        copy.addProperty("isArchived", archived);
        return copy;
    }

    private static JsonArray featureFlags(JsonObject envelope) {
        return envelope.getAsJsonObject("data").getAsJsonArray("featureFlags");
    }

    private static DataModel.Data data(JsonObject envelope) {
        return JsonHelper.deserialize(envelope.toString(), DataModel.All.class).data();
    }

    private static JsonObject envelope() throws Exception {
        String json = Resources.toString(Resources.getResource("fbclient_test_data.json"), Charsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
