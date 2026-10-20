package co.featbit.example;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FBClient;
import co.featbit.server.integrations.FBUserContextHolder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "featbit.offline=true")
@AutoConfigureMockMvc
class ApplicationTest {
    private static final AtomicInteger revision = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserContextFilter filter;

    @Autowired
    private FBClient client;

    @Autowired
    private UserService userService;

    @AfterEach
    void clearUser() {
        FBUserContextHolder.remove();
    }

    @Test
    void routesIndependentFlagsAndIsolatesRequests() throws Exception {
        assertName(get("/name"), "albert", "einstein");
        assertName(get("/name").header("X-User-Key", "formatted"), "Albert", "E.");
        assertName(get("/name"), "albert", "einstein");
        assertName(get("/name").header("X-User-Key", "first-only"), "Albert", "einstein");
        assertName(get("/name").header("X-User-Key", "last-only"), "albert", "E.");
        assertName(get("/name").header("X-User-Key", "unknown"), "albert", "einstein");
    }

    @Test
    void replacesStaleContextAndClearsItWhenTheRequestFails() {
        FBUserContextHolder.setCurrentUser(new FBUser.Builder("stale").userName("stale").build(), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Key", "formatted");
        ServletException failure = new ServletException("Request failed");

        assertSame(failure, assertThrows(ServletException.class, () -> filter.doFilter(request,
                new MockHttpServletResponse(), (req, response) -> {
                    assertEquals("formatted", FBUserContextHolder.getCurrentUser().getKey());
                    throw failure;
                })));
        assertNull(FBUserContextHolder.getCurrentUser());
    }

    @Test
    void observesRolloutUpdatesAndFallbacksWithinTheSameService() throws IOException {
        try {
            FBUserContextHolder.setCurrentUser(new FBUser.Builder("rollout-user").userName("rollout-user").build(), false);
            updateFirstName(0, "v2", false);
            assertEquals("albert einstein", userService.fullName());
            updateFirstName(1, "v2", false);
            assertEquals("Albert einstein", userService.fullName());
            updateFirstName(0, "v2", false);
            assertEquals("albert einstein", userService.fullName());
            updateFirstName(1, "unmapped", false);
            assertEquals("albert einstein", userService.fullName());
            updateFirstName(1, "v2", true);
            assertEquals("albert einstein", userService.fullName());
            updateFirstName(1, "v2", false);
            FBUserContextHolder.remove();
            assertEquals("albert einstein", userService.fullName());
        } finally {
            FBUserContextHolder.remove();
            assertTrue(client.initializeFromExternalJson(fixture().toString()));
        }
    }

    private void updateFirstName(double share, String value, boolean missing) throws IOException {
        JsonObject document = fixture();
        JsonArray flags = document.getAsJsonObject("data").getAsJsonArray("featureFlags");
        JsonObject flag = flags.get(0).getAsJsonObject();
        flag.add("targetUsers", new JsonArray());
        flag.getAsJsonArray("variations").get(1).getAsJsonObject().addProperty("value", value);
        JsonArray variations = new JsonArray();
        variations.add(rollout("v1", 0, 1 - share));
        variations.add(rollout("v2", 1 - share, 1));
        flag.getAsJsonObject("fallthrough").add("variations", variations);
        if (missing) {
            flags.remove(0);
        }
        assertTrue(client.initializeFromExternalJson(document.toString()));
    }

    private JsonObject fixture() throws IOException {
        String json = new ClassPathResource("flags.json").getContentAsString(StandardCharsets.UTF_8);
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        String updatedAt = Instant.parse("2030-01-01T00:00:00Z").plusSeconds(revision.incrementAndGet()).toString();
        for (JsonElement flag : document.getAsJsonObject("data").getAsJsonArray("featureFlags")) {
            flag.getAsJsonObject().addProperty("updatedAt", updatedAt);
        }
        return document;
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

    private void assertName(MockHttpServletRequestBuilder request, String firstName, String lastName) throws Exception {
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(firstName))
                .andExpect(jsonPath("$.lastName").value(lastName))
                .andExpect(jsonPath("$.fullName").value(firstName + " " + lastName));
        assertNull(FBUserContextHolder.getCurrentUser());
    }
}
