package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.featbit.server.Evaluator.REASON_FALLTHROUGH;
import static co.featbit.server.Evaluator.REASON_FLAG_OFF;
import static co.featbit.server.Evaluator.REASON_RULE_MATCH;
import static co.featbit.server.Evaluator.REASON_TARGET_MATCH;
import static co.featbit.server.exterior.DataStorageTypes.FEATURES;
import static co.featbit.server.exterior.DataStorageTypes.SEGMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationTest extends ComponentBaseTest {

    DataStorage dataStorage;

    Evaluator evaluator;

    DataModel.FeatureFlag flag;

    DataModel.FeatureFlag disabledFlag;

    FBUser user1 = new FBUser.Builder("test-user-1").userName("test-user-1").build();
    FBUser user2 = new FBUser.Builder("test-target-user").userName("test-target-user").build();
    FBUser user3 = new FBUser.Builder("test-true-user").userName("test-true-user").custom("graduated", "true").build();
    FBUser user4 = new FBUser.Builder("test-equal-user").userName("test-equal-user").custom("country", "CHN").build();
    FBUser user5 = new FBUser.Builder("test-than-user").userName("test-than-user").custom("salary", "2500").build();
    FBUser user6 = new FBUser.Builder("test-contain-user").userName("test-contain-user").custom("email", "test-contain-user@gmail.com").build();
    FBUser user7 = new FBUser.Builder("test-isoneof-user").userName("test-isoneof-user").custom("major", "CS").build();
    FBUser user8 = new FBUser.Builder("group-admin-user").userName("group-admin-user").build();
    FBUser user9 = new FBUser.Builder("test-regex-user").userName("test-regex-user").custom("phone", "18555358000").build();
    FBUser user10 = new FBUser.Builder("test-fallthrough-user").userName("test-fallthrough-user").build();

    @BeforeEach
    void init() throws Exception {
        dataStorage = new InMemoryDataStorage();
        DataModel.Data data = loadData();
        dataStorage.init(data.toStorageType(), data.getTimestamp());

        Evaluator.Getter<DataModel.FeatureFlag> flagGetter = key -> {
            DataStorageTypes.Item item = dataStorage.get(FEATURES, key);
            return item == null ? null : (DataModel.FeatureFlag) item;
        };

        Evaluator.Getter<DataModel.Segment> segmentGetter = key -> {
            DataStorageTypes.Item item = dataStorage.get(SEGMENTS, key);
            return item == null ? null : (DataModel.Segment) item;
        };

        evaluator = new EvaluatorImp(flagGetter, segmentGetter);

        flag = flagGetter.get("ff-evaluation-test");

        disabledFlag = flagGetter.get("ff-test-off");

    }

    @Test
    void testEvaluationWhenDisabledFlag() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user1);
        Evaluator.EvalResult res = evaluator.evaluate(disabledFlag, user1, event);
        assertEquals("false", res.getValue());
        assertEquals(REASON_FLAG_OFF, res.getReason());
    }

    @Test
    void testEvaluationWhenMatchTargetUser() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user2);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user2, event);
        assertEquals("teamB", res.getValue());
        assertEquals(REASON_TARGET_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchTrueCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user3);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user3, event);
        assertEquals("teamC", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchEqualCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user4);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user4, event);
        assertEquals("teamD", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchThanCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user5);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user5, event);
        assertEquals("teamE", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchContainCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user6);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user6, event);
        assertEquals("teamF", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchIsOneOfCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user7);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user7, event);
        assertEquals("teamG", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchStartEndCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user8);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user8, event);
        assertEquals("teamH", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenMarchRegexCondition() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user9);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user9, event);
        assertEquals("teamI", res.getValue());
        assertEquals(REASON_RULE_MATCH, res.getReason());
    }

    @Test
    void testEvaluationWhenFallThrough() {
        InsightTypes.Event event = InsightTypes.FlagEvent.of(user10);
        Evaluator.EvalResult res = evaluator.evaluate(flag, user10, event);
        assertEquals("teamA", res.getValue());
        assertEquals(REASON_FALLTHROUGH, res.getReason());
    }

}
