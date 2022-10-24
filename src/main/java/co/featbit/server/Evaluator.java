package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.DataStoreTypes;
import org.slf4j.Logger;

/**
 * Evaluation process is totally isolated from update process and data storage
 */

abstract class Evaluator {

    protected static final Logger logger = Loggers.EVALUATION;
    protected static final String NO_EVAL_RES = "NE";
    protected static final String DEFAULT_JSON_VALUE = "DJV";
    protected static final String REASON_USER_NOT_SPECIFIED = "user not specified";
    protected static final String REASON_FLAG_OFF = "flag off";
    protected static final String REASON_TARGET_MATCH = "target match";
    protected static final String REASON_RULE_MATCH = "rule match";
    protected static final String REASON_FALLTHROUGH = "fall through all rules";
    protected static final String REASON_CLIENT_NOT_READY = "client not ready";
    protected static final String REASON_FLAG_NOT_FOUND = "flag not found";
    protected static final String REASON_WRONG_TYPE = "wrong type";
    protected static final String REASON_ERROR = "error in evaluation";
    protected static final String FLAG_KEY_UNKNOWN = "flag key unknown";
    protected static final String FLAG_NAME_UNKNOWN = "flag name unknown";
    protected static final String FLAG_VALUE_UNKNOWN = "flag value unknown";
    protected static final String THAN_CLAUSE = "Than";
    protected static final String GE_CLAUSE = "BiggerEqualThan";
    protected static final String GT_CLAUSE = "BiggerThan";
    protected static final String LE_CLAUSE = "LessEqualThan";
    protected static final String LT_CLAUSE = "LessThan";
    protected static final String EQ_CLAUSE = "Equal";
    protected static final String NEQ_CLAUSE = "NotEqual";
    protected static final String CONTAINS_CLAUSE = "Contains";
    protected static final String NOT_CONTAIN_CLAUSE = "NotContain";
    protected static final String IS_ONE_OF_CLAUSE = "IsOneOf";
    protected static final String NOT_ONE_OF_CLAUSE = "NotOneOf";
    protected static final String STARTS_WITH_CLAUSE = "StartsWith";
    protected static final String ENDS_WITH_CLAUSE = "EndsWith";
    protected static final String IS_TRUE_CLAUSE = "IsTrue";
    protected static final String IS_FALSE_CLAUSE = "IsFalse";
    protected static final String MATCH_REGEX_CLAUSE = "MatchRegex";
    protected static final String NOT_MATCH_REGEX_CLAUSE = "NotMatchRegex";
    protected static final String IS_IN_SEGMENT_CLAUSE = "User is in segment";
    protected static final String NOT_IN_SEGMENT_CLAUSE = "User is not in segment";

    protected static final String FLAG_JSON_TYPE = "json";

    protected static final String FLAG_BOOL_TYPE = "boolean";

    protected static final String FLAG_NUMERIC_TYPE = "number";

    protected static final String FLAG_STRING_TYPE = "string";

    protected final Getter<DataModel.FeatureFlag> flagGetter;

    protected final Getter<DataModel.Segment> segmentGetter;

    Evaluator(Getter<DataModel.FeatureFlag> flagGetter,
              Getter<DataModel.Segment> segmentGetter) {
        this.flagGetter = flagGetter;
        this.segmentGetter = segmentGetter;
    }

    abstract EvalResult evaluate(DataModel.FeatureFlag flag, FBUser user, InsightTypes.Event event);

    @FunctionalInterface
    interface Getter<T extends DataStoreTypes.Item> {
        T get(String key);
    }

    static class EvalResult {
        private final String index;
        private final String value;
        private final String reason;
        private final boolean sendToExperiment;
        private final String keyName;
        private final String name;


        EvalResult(String value, String index, String reason, boolean sendToExperiment, String keyName, String name) {
            this.value = value;
            this.index = index;
            this.reason = reason;
            this.sendToExperiment = sendToExperiment;
            this.keyName = keyName;
            this.name = name;
        }

        public static EvalResult error(String reason, String keyName, String name) {
            return new EvalResult(null, NO_EVAL_RES, reason, false, keyName, name);
        }

        public static EvalResult error(String defaultValue, String reason, String keyName, String name) {
            return new EvalResult(defaultValue, NO_EVAL_RES, reason, false, keyName, name);
        }

        public static EvalResult of(DataModel.Variation option,
                                    String reason,
                                    boolean sendToExperiment,
                                    String keyName,
                                    String name) {
            return new EvalResult(option.getValue(),
                    option.getId(),
                    reason,
                    sendToExperiment,
                    keyName,
                    name);
        }

        public String getValue() {
            return value;
        }

        public String getIndex() {
            return index;
        }

        public String getReason() {
            return reason;
        }

        public boolean isSendToExperiment() {
            return sendToExperiment;
        }

        public String getKeyName() {
            return keyName;
        }

        public String getName() {
            return name;
        }
    }

}
