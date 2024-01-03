package co.featbit.server;

public interface EvaluationReason {
    String REASON_USER_NOT_SPECIFIED = "user not specified";
    String REASON_FLAG_OFF = "flag off";
    String REASON_TARGET_MATCH = "target match";
    String REASON_RULE_MATCH = "rule match";
    String REASON_FALLTHROUGH = "fall through all rules";
    String REASON_CLIENT_NOT_READY = "client not ready";
    String REASON_FLAG_NOT_FOUND = "flag not found";
    String REASON_WRONG_TYPE = "wrong type";
    String REASON_ERROR = "error in evaluation";
}
