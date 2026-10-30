package co.featbit.server;

import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static co.featbit.server.EvaluationReason.REASON_ERROR;
import static co.featbit.server.EvaluationReason.REASON_FALLTHROUGH;
import static co.featbit.server.EvaluationReason.REASON_RULE_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Included users, segment included/excluded keys and IsOneOf/NotOneOf values are indexed when data is synced.
 * These tests check that evaluation follows each replacement of that data, including under concurrent evaluation.
 */
class ListTargetingTest {
    private static final String FLAG = "list-flag";
    private static final String SEGMENT_ID = "segment-id";
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final Instant FIRST_UPDATE = Instant.parse("2024-01-01T00:00:00Z");

    private FBClientImp client;
    private int generation;

    @BeforeEach
    void init() {
        client = new FBClientImp("", new FBConfig.Builder()
                .offline(true)
                .streamingURL("http://localhost:1")
                .eventURL("http://localhost:1")
                .build());
    }

    @AfterEach
    void dispose() throws IOException {
        client.close();
    }

    @Test
    void testIsOneOfInSegmentFollowsReplacedValues() {
        syncSegment(userIdRule("IsOneOf", 0, 1, 2), Collections.emptyList(), Collections.emptyList());
        assertTrue(evaluate(1));
        assertFalse(evaluate(9));

        syncSegment(userIdRule("IsOneOf", 2, 9), Collections.emptyList(), Collections.emptyList());
        assertFalse(evaluate(1));
        assertTrue(evaluate(2));
        assertTrue(evaluate(9));
    }

    @Test
    void testNotOneOfFollowsReplacedValues() {
        syncFlagRule(condition("user_id", "NotOneOf", userIds(0, 1)));
        assertFalse(evaluate(1));
        assertTrue(evaluate(9));

        syncFlagRule(condition("user_id", "NotOneOf", userIds(9)));
        assertTrue(evaluate(1));
        assertFalse(evaluate(9));
    }

    @Test
    void testIncludedUsersFollowReplacedKeys() {
        syncFlag(targetUsers(0, 1), new JsonArray(), new JsonArray());
        assertTrue(evaluate(1));
        assertFalse(evaluate(5));

        syncFlag(targetUsers(5), new JsonArray(), new JsonArray());
        assertFalse(evaluate(1));
        assertTrue(evaluate(5));
    }

    @Test
    void testSegmentExcludedKeysWinAndFollowReplacement() {
        syncSegment(null, Arrays.asList(key(0), key(1)), Collections.singletonList(key(1)));
        assertTrue(evaluate(0));
        assertFalse(evaluate(1));
        assertFalse(evaluate(2));

        syncSegment(null, Collections.singletonList(key(1)), Collections.emptyList());
        assertFalse(evaluate(0));
        assertTrue(evaluate(1));
    }

    @Test
    void testOtherOperatorsStillUseTheRawValue() {
        syncFlagRule(condition("user_id", "Equal", userId(3)));
        assertTrue(evaluate(3));
        assertFalse(evaluate(4));

        syncFlagRule(condition("user_id", "MatchRegex", "user-1[0-9]"));
        assertFalse(evaluate(3));
        assertTrue(evaluate(11));
    }

    @Test
    void testInvalidIsOneOfValueReturnsTheDefaultWithAnError() {
        syncFlagRule(condition("user_id", "IsOneOf", "not a list"));
        EvalDetail<Boolean> detail = detail(1, true);
        assertTrue(detail.getVariation());
        assertEquals(REASON_ERROR, detail.getReason());
    }

    @Test
    void testConcurrentEvaluationsDuringReplacements() throws Exception {
        int size = 1000;
        int[] first = IntStream.range(0, size).toArray();
        int[] second = IntStream.range(size, 2 * size).toArray();
        syncSegment(userIdRule("IsOneOf", first), Collections.emptyList(), Collections.emptyList());

        AtomicBoolean done = new AtomicBoolean();
        ExecutorService readers = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int t = 0; t < 4; t++) {
                results.add(readers.submit(() -> {
                    int evaluations = 0;
                    while (!done.get()) {
                        for (int index : new int[]{0, size - 1, size, 2 * size - 1}) {
                            String reason = detail(index, false).getReason();
                            if (!reason.equals(REASON_RULE_MATCH) && !reason.equals(REASON_FALLTHROUGH)) {
                                throw new AssertionError("user " + index + ": " + reason);
                            }
                            evaluations++;
                        }
                    }
                    return evaluations;
                }));
            }
            for (int i = 0; i < 200; i++) {
                syncSegment(userIdRule("IsOneOf", i % 2 == 0 ? second : first), Collections.emptyList(), Collections.emptyList());
            }
        } finally {
            done.set(true);
            readers.shutdown();
        }
        for (Future<Integer> result : results) {
            assertTrue(result.get() > 0);
        }
        // an even number of replacements ends on the first list
        assertTrue(evaluate(0));
        assertFalse(evaluate(size));
    }

    private boolean evaluate(int index) {
        return detail(index, false).getVariation();
    }

    private EvalDetail<Boolean> detail(int index, boolean defaultValue) {
        FBUser user = new FBUser.Builder(key(index)).userName(key(index)).custom("user_id", userId(index)).build();
        return client.boolVariationDetail(FLAG, user, defaultValue);
    }

    private void syncSegment(JsonObject rule, List<String> included, List<String> excluded) {
        JsonObject segment = new JsonObject();
        segment.addProperty("id", SEGMENT_ID);
        segment.addProperty("isArchived", false);
        segment.add("included", strings(included));
        segment.add("excluded", strings(excluded));
        JsonArray rules = new JsonArray();
        if (rule != null) {
            rules.add(rule);
        }
        segment.add("rules", rules);
        JsonArray segments = new JsonArray();
        segments.add(segment);

        JsonArray segmentIds = new JsonArray();
        segmentIds.add(SEGMENT_ID);
        JsonArray flagRules = new JsonArray();
        flagRules.add(flagRule(condition("User is in segment", "", segmentIds.toString())));
        syncFlag(new JsonArray(), flagRules, segments);
    }

    private void syncFlagRule(JsonObject condition) {
        JsonArray rules = new JsonArray();
        rules.add(flagRule(condition));
        syncFlag(new JsonArray(), rules, new JsonArray());
    }

    private void syncFlag(JsonArray targetUsers, JsonArray rules, JsonArray segments) {
        // the SDK only accepts data newer than what it holds
        String updatedAt = FIRST_UPDATE.plusSeconds(++generation).toString();
        JsonObject fallthrough = new JsonObject();
        fallthrough.addProperty("dispatchKey", "keyId");
        fallthrough.addProperty("includedInExpt", true);
        fallthrough.add("variations", rollout(OFF));

        JsonArray variations = new JsonArray();
        variations.add(variation(ON, "true"));
        variations.add(variation(OFF, "false"));

        JsonObject flag = new JsonObject();
        flag.addProperty("id", FLAG + "-id");
        flag.addProperty("key", FLAG);
        flag.addProperty("name", FLAG);
        flag.addProperty("updatedAt", updatedAt);
        flag.addProperty("isEnabled", true);
        flag.addProperty("variationType", "boolean");
        flag.add("variations", variations);
        flag.addProperty("disabledVariationId", OFF);
        flag.add("targetUsers", targetUsers);
        flag.add("rules", rules);
        flag.add("fallthrough", fallthrough);
        flag.addProperty("exptIncludeAllTargets", true);
        JsonArray flags = new JsonArray();
        flags.add(flag);
        for (JsonElement segment : segments) {
            segment.getAsJsonObject().addProperty("updatedAt", updatedAt);
        }

        JsonObject data = new JsonObject();
        data.addProperty("eventType", "full");
        data.add("featureFlags", flags);
        data.add("segments", segments);
        JsonObject message = new JsonObject();
        message.addProperty("messageType", "data-sync");
        message.add("data", data);
        assertTrue(client.initializeFromExternalJson(message.toString()));
    }

    private static JsonArray targetUsers(int... indexes) {
        JsonArray keyIds = new JsonArray();
        for (int index : indexes) {
            keyIds.add(key(index));
        }
        JsonObject targetUser = new JsonObject();
        targetUser.add("keyIds", keyIds);
        targetUser.addProperty("variationId", ON);
        JsonArray targetUsers = new JsonArray();
        targetUsers.add(targetUser);
        return targetUsers;
    }

    private static JsonObject userIdRule(String op, int... indexes) {
        JsonArray conditions = new JsonArray();
        conditions.add(condition("user_id", op, userIds(indexes)));
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "user-id-rule");
        rule.addProperty("name", "user ids");
        rule.add("conditions", conditions);
        return rule;
    }

    private static JsonObject flagRule(JsonObject condition) {
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        JsonObject rule = new JsonObject();
        rule.addProperty("id", "flag-rule");
        rule.addProperty("name", "flag rule");
        rule.addProperty("dispatchKey", "keyId");
        rule.addProperty("includedInExpt", false);
        rule.add("conditions", conditions);
        rule.add("variations", rollout(ON));
        return rule;
    }

    private static JsonObject condition(String property, String op, String value) {
        JsonObject condition = new JsonObject();
        condition.addProperty("property", property);
        condition.addProperty("op", op);
        condition.addProperty("value", value);
        return condition;
    }

    private static JsonArray rollout(String variationId) {
        JsonArray range = new JsonArray();
        range.add(0);
        range.add(1);
        JsonObject variation = new JsonObject();
        variation.addProperty("id", variationId);
        variation.add("rollout", range);
        variation.addProperty("exptRollout", 1);
        JsonArray variations = new JsonArray();
        variations.add(variation);
        return variations;
    }

    private static JsonObject variation(String id, String value) {
        JsonObject variation = new JsonObject();
        variation.addProperty("id", id);
        variation.addProperty("name", value);
        variation.addProperty("value", value);
        return variation;
    }

    private static String userIds(int... indexes) {
        JsonArray userIds = new JsonArray();
        for (int index : indexes) {
            userIds.add(userId(index));
        }
        return userIds.toString();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static String key(int index) {
        return "key-" + index;
    }

    private static String userId(int index) {
        return "user-" + index;
    }
}
