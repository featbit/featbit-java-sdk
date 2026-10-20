package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import co.featbit.server.FBClientBaseTest;
import co.featbit.server.exterior.FBClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FeatGateEvaluationTest extends FBClientBaseTest {
    @AfterEach
    void clearUser() {
        FBUserContextHolder.remove();
    }

    @Test
    void evaluatesUserPropertiesAndMissingFlagsWithTheRealSdk() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            Service service = FeatGateProxy.create(Service.class, new Target(), client);
            FBUserContextHolder.setCurrentUser(user("american", "us"), false);
            assertEquals("US", service.region());
            FBUserContextHolder.setCurrentUser(user("chinese", "cn"), false);
            assertEquals("CN", service.region());
            assertEquals("original", service.missing());
            FBUserContextHolder.remove();
            assertEquals("other", service.region());
        }
    }

    @Test
    void observesPercentageUpdatesOnTheSameProxyAndUsesTheDisabledVariation() throws IOException {
        try (FBClient client = initClientInOfflineMode()) {
            Service service = FeatGateProxy.create(Service.class, new Target(), client);
            assertTrue(client.initializeFromExternalJson(percentageFlags(0.25, 1, true)));
            int selected = 0;
            FBUser oldUser = null;
            FBUser newUser = null;
            for (int i = 0; i < 128; i++) {
                FBUser user = user("rollout-user-" + i, "us");
                FBUserContextHolder.setCurrentUser(user, false);
                String value = service.format();
                assertEquals(value, service.format());
                if ("formatted".equals(value)) {
                    selected++;
                    newUser = user;
                } else {
                    oldUser = user;
                }
            }
            assertTrue(selected > 0 && selected < 128);
            assertTrue(client.initializeFromExternalJson(percentageFlags(1, 2, true)));
            FBUserContextHolder.setCurrentUser(oldUser, false);
            assertEquals("formatted", service.format());
            assertTrue(client.initializeFromExternalJson(percentageFlags(0, 3, true)));
            FBUserContextHolder.setCurrentUser(newUser, false);
            assertEquals("original", service.format());
            assertTrue(client.initializeFromExternalJson(percentageFlags(0, 4, false)));
            assertEquals("formatted", service.format());
        }
    }

    private FBUser user(String key, String country) {
        return new FBUser.Builder(key).userName(key).custom("country", country).build();
    }

    private String percentageFlags(double share, int version, boolean enabled) throws IOException {
        JsonObject document = JsonParser.parseString(readResource("fbclient_test_data.json")).getAsJsonObject();
        JsonObject data = document.getAsJsonObject("data");
        for (JsonElement element : data.getAsJsonArray("featureFlags")) {
            JsonObject flag = element.getAsJsonObject();
            if (!"ff-test-bool".equals(flag.get("key").getAsString())) {
                continue;
            }
            flag.addProperty("updatedAt", "2026-10-20T00:00:0" + version + "Z");
            flag.addProperty("isEnabled", enabled);
            flag.add("targetUsers", new JsonArray());
            String on = flag.getAsJsonArray("variations").get(0).getAsJsonObject().get("id").getAsString();
            String off = flag.getAsJsonArray("variations").get(1).getAsJsonObject().get("id").getAsString();
            flag.addProperty("disabledVariationId", on);
            JsonArray variations = new JsonArray();
            variations.add(rollout(on, 0, share));
            variations.add(rollout(off, share, 1));
            flag.getAsJsonObject("fallthrough").addProperty("dispatchKey", "keyid");
            flag.getAsJsonObject("fallthrough").add("variations", variations);
            JsonArray flags = new JsonArray();
            flags.add(flag);
            data.add("featureFlags", flags);
            return document.toString();
        }
        throw new IllegalStateException("Missing boolean fixture flag");
    }

    private JsonObject rollout(String id, double lower, double upper) {
        JsonObject variation = new JsonObject();
        variation.addProperty("id", id);
        JsonArray range = new JsonArray();
        range.add(lower);
        range.add(upper);
        variation.add("rollout", range);
        return variation;
    }

    public interface Service {
        String region();
        String format();
        String missing();
    }

    public static class Target implements Service {
        @FeatGate(flag = "ff-test-number", variants = {
                @FeatGate.Variant(value = "1", method = "us"),
                @FeatGate.Variant(value = "86", method = "cn")})
        public String region() { return "other"; }
        private String us() { return "US"; }
        private String cn() { return "CN"; }

        @FeatGate(flag = "ff-test-bool", variants = @FeatGate.Variant(value = "true", method = "formatted"))
        public String format() { return "original"; }
        private String formatted() { return "formatted"; }

        @FeatGate(flag = "missing-flag", variants = @FeatGate.Variant(value = "true", method = "formatted"))
        public String missing() { return "original"; }
    }
}
