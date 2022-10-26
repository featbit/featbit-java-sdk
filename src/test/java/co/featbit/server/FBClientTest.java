package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.commons.model.FlagState;
import co.featbit.server.exterior.FBClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static co.featbit.server.Evaluator.REASON_FALLTHROUGH;
import static co.featbit.server.Evaluator.REASON_RULE_MATCH;
import static co.featbit.server.Evaluator.REASON_TARGET_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FBClientTest extends FBClientBaseTest {
    private static FBClient client;

    private static FBUser user1;
    private static FBUser user2;
    private static FBUser user3;
    private static FBUser user4;
    private static FBUser cnPhoneNumber;
    private static FBUser frPhoneNumber;
    private static FBUser email;
    private static FBUser dummy;

    @BeforeAll
    public static void init() throws IOException {
        client = initClientInOfflineMode();
        user1 = new FBUser.Builder("test-user-1").userName("test-user-1").custom("country", "us").build();
        user2 = new FBUser.Builder("test-user-2").userName("test-user-2").custom("country", "fr").build();
        user3 = new FBUser.Builder("test-user-3").userName("test-user-3").custom("country", "cn").custom("major", "cs").build();
        user4 = new FBUser.Builder("test-user-4").userName("test-user-4").custom("country", "uk").custom("major", "physics").build();
        cnPhoneNumber = new FBUser.Builder("18555358000").userName("test-user-5").build();
        frPhoneNumber = new FBUser.Builder("0603111111").userName("test-user-6").build();
        email = new FBUser.Builder("test-user-7@featbit.com").userName("test-user-7").build();
        dummy = new FBUser.Builder("12345").userName("dummy").build();
    }

    @AfterAll
    public static void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testBoolVariation() {
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

    @Test
    public void testNumericVariation() {
        //get country numbers according to user's country
        // us=1, fr=33, cn=86, others=9999
        int res = client.intVariation("ff-test-number", user1, -1);
        assertTrue(res == 1);
        FlagState<Long> state = client.longVariationDetail("ff-test-number", user2, -1L);
        assertTrue(state.getData().getVariation() == 33L);
        assertEquals(REASON_RULE_MATCH, state.getData().getReason());
        double res1 = client.doubleVariation("ff-test-number", user3, -1D);
        assertTrue(res1 == 86D);
        FlagState<Double> state1 = client.doubleVariationDetail("ff-test-number", user4, -1D);
        assertTrue(state1.getData().getVariation() == 9999D);
        assertEquals(REASON_FALLTHROUGH, state1.getData().getReason());
    }

    @Test
    public void testStringVariation() {
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

    @Test
    public void testSegment() {
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

    @Test
    public void testJsonVariation() {
        //dummy game: 25% win 100 euros
        Dummy dummy1 = client.jsonVariation("ff-test-json", dummy, Dummy.class, null);
        assertEquals(200, dummy1.code);
        FlagState<Dummy> dummy2 = client.jsonVariationDetail("ff-test-json", user1, Dummy.class, null);
        assertEquals(404, dummy2.getData().getVariation().code);
        assertEquals(REASON_FALLTHROUGH, dummy2.getData().getReason());
    }

}
