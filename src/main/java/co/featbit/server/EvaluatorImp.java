package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.model.FBUser;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

final class EvaluatorImp extends Evaluator {

    EvaluatorImp(Getter<DataModel.FeatureFlag> flagGetter, Getter<DataModel.Segment> segmentGetter) {
        super(flagGetter, segmentGetter);
    }

    @Override
    EvalResult evaluate(DataModel.FeatureFlag flag, FBUser user, InsightTypes.Event event) {
        if (user == null || flag == null) {
            throw new IllegalArgumentException("null flag or empty user");
        }
        return matchUserVariation(flag, user, event);

    }

    private EvalResult matchUserVariation(DataModel.FeatureFlag flag, FBUser user, InsightTypes.Event event) {
        //return a value when flag is off or not match prerequisite rule
        EvalResult er = null;
        try {
            er = matchFeatureFlagDisabledUserVariation(flag);
            if (er != null) {
                return er;
            }
            //return the value of target user
            er = matchTargetedUserVariation(flag, user);
            if (er != null) return er;

            //return the value of matched rule
            er = matchConditionedUserVariation(flag, user);
            if (er != null) {
                return er;
            }
            //get value from default rule
            er = matchFallThroughUserVariation(flag, user);
            return er;
        } finally {
            if (er != null) {
                logger.info("FFC JAVA SDK: User {}, Feature Flag {}, Flag Value {}", user.getKey(), flag.getKey(), er.getValue());
                if (event != null) {
                    event.add(InsightTypes.FlagEventVariation.of(flag.getKey(), er));
                }
            }
        }
    }

    private EvalResult matchFeatureFlagDisabledUserVariation(DataModel.FeatureFlag flag) {
        // case flag is off
        if (!flag.isEnabled()) {
            return EvalResult.of(flag.getVariation(flag.getDisabledVariationId()),
                    REASON_FLAG_OFF,
                    false,
                    flag.getKey(), flag.getName());
        }
        return null;
    }

    private EvalResult matchTargetedUserVariation(DataModel.FeatureFlag featureFlag, FBUser user) {
        return featureFlag.getTargetUsers().stream()
                .filter(target -> target.isTargeted(user.getKey()))
                .findFirst()
                .map(target -> EvalResult.of(featureFlag.getVariation(target.getVariationId()),
                        REASON_TARGET_MATCH,
                        featureFlag.exptIncludeAllTargets(),
                        featureFlag.getKey(),
                        featureFlag.getName()))
                .orElse(null);
    }

    private EvalResult matchConditionedUserVariation(DataModel.FeatureFlag featureFlag, FBUser user) {
        DataModel.TargetRule targetRule = featureFlag.getRules().stream().filter(rule -> ifUserMatchRule(user, rule.getConditions())).findFirst().orElse(null);
        // optional flatmap can't infer inner type of collection
        return targetRule == null ? null : getRollOutVariationOption(featureFlag,
                targetRule.getVariations(),
                user,
                REASON_RULE_MATCH,
                featureFlag.exptIncludeAllTargets(),
                targetRule.includedInExpt(),
                featureFlag.getKey(),
                featureFlag.getName());
    }

    private boolean ifUserMatchRule(FBUser user, List<DataModel.Condition> conditions) {
        return conditions.stream().allMatch(condition -> ifUserMatchClause(user, condition));
    }

    private boolean ifUserMatchClause(FBUser user, DataModel.Condition condition) {
        String op = condition.getOp();
        // segment hasn't any operation
        op = StringUtils.isBlank(op) ? condition.getProperty() : op;
        if (op.contains(THAN_CLAUSE)) {
            return thanClause(user, condition);
        } else if (op.equals(EQ_CLAUSE)) {
            return equalsClause(user, condition);
        } else if (op.equals(NEQ_CLAUSE)) {
            return !equalsClause(user, condition);
        } else if (op.equals(CONTAINS_CLAUSE)) {
            return containsClause(user, condition);
        } else if (op.equals(NOT_CONTAIN_CLAUSE)) {
            return !containsClause(user, condition);
        } else if (op.equals(IS_ONE_OF_CLAUSE)) {
            return oneOfClause(user, condition);
        } else if (op.equals(NOT_ONE_OF_CLAUSE)) {
            return !oneOfClause(user, condition);
        } else if (op.equals(STARTS_WITH_CLAUSE)) {
            return startsWithClause(user, condition);
        } else if (op.equals(ENDS_WITH_CLAUSE)) {
            return endsWithClause(user, condition);
        } else if (op.equals(IS_TRUE_CLAUSE)) {
            return trueClause(user, condition);
        } else if (op.equals(IS_FALSE_CLAUSE)) {
            return !trueClause(user, condition);
        } else if (op.equals(MATCH_REGEX_CLAUSE)) {
            return matchRegExClause(user, condition);
        } else if (op.equals(NOT_MATCH_REGEX_CLAUSE)) {
            return !matchRegExClause(user, condition);
        } else if (op.equals(IS_IN_SEGMENT_CLAUSE)) {
            return inSegmentClause(user, condition);
        } else if (op.equals(NOT_IN_SEGMENT_CLAUSE)) {
            return !inSegmentClause(user, condition);
        }
        return false;
    }

    private boolean inSegmentClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getKey();
        try {
            List<String> segments = JsonHelper.deserialize(condition.getValue(), new TypeToken<List<String>>() {
            }.getType());
            return segments.stream().map(segmentGetter::get).anyMatch(segment -> {
                if (segment == null) {
                    return false;
                }
                Boolean userInSegment = segment.isMatchUser(pv);
                if (userInSegment == null) {
                    return segment
                            .getRules()
                            .stream()
                            .anyMatch(rule -> ifUserMatchRule(user, rule.getConditions()));
                }
                return userInSegment;
            });
        } catch (JsonParseException e) {
            return false;
        }
    }

    private boolean trueClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        return pv != null && BooleanUtils.toBoolean(pv);
    }

    private boolean matchRegExClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        return pv != null && Pattern.compile(condValue).matcher(pv).matches();
    }

    private boolean endsWithClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        return pv != null && pv.endsWith(condValue);
    }

    private boolean startsWithClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        return pv != null && pv.startsWith(condValue);
    }

    private boolean thanClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        if (!StringUtils.isNumeric(pv) || !StringUtils.isNumeric(condValue)) {
            return false;
        }
        double pvNumber = new BigDecimal(pv).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double cvNumber = new BigDecimal(condValue).setScale(5, RoundingMode.HALF_UP).doubleValue();
        switch (condition.getOp()) {
            case GE_CLAUSE:
                return pvNumber >= cvNumber;
            case GT_CLAUSE:
                return pvNumber > cvNumber;
            case LE_CLAUSE:
                return pvNumber <= cvNumber;
            case LT_CLAUSE:
                return pvNumber < cvNumber;
            default:
                return false;
        }
    }

    private boolean equalsClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        return condValue.equals(pv);
    }

    private boolean containsClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        String condValue = condition.getValue();
        return pv != null && pv.contains(condValue);
    }

    private boolean oneOfClause(FBUser user, DataModel.Condition condition) {
        String pv = user.getProperty(condition.getProperty());
        try {
            List<String> clauseValues = JsonHelper.deserialize(condition.getValue(), new TypeToken<List<String>>() {
            }.getType());
            return pv != null && clauseValues.contains(pv);
        } catch (JsonParseException e) {
            return false;
        }
    }

    private EvalResult matchFallThroughUserVariation(DataModel.FeatureFlag featureFlag, FBUser user) {
        DataModel.Fallthrough fallthrough = featureFlag.getFallthrough();
        return getRollOutVariationOption(
                featureFlag,
                fallthrough.getVariations(),
                user,
                REASON_FALLTHROUGH,
                featureFlag.exptIncludeAllTargets(),
                fallthrough.includedInExpt(),
                featureFlag.getKey(),
                featureFlag.getName());
    }

    private EvalResult getRollOutVariationOption(DataModel.FeatureFlag featureFlag,
                                                 Collection<DataModel.RolloutVariation> rollouts,
                                                 FBUser user,
                                                 String reason,
                                                 Boolean exptIncludeAllTargets,
                                                 Boolean ruleIncludedInExperiment,
                                                 String flagKeyName,
                                                 String flagName) {
        String newUserKey = Base64.getEncoder().encodeToString(user.getKey().getBytes());
        return rollouts.stream()
                .filter(rollout -> VariationSplittingAlgorithm.ifKeyBelongsPercentage(user.getKey(), rollout.getRollout()))
                .findFirst()
                .map(rollout -> EvalResult.of(featureFlag.getVariation(rollout.getId()),
                        reason,
                        isSendToExperiment(newUserKey, rollout, exptIncludeAllTargets, ruleIncludedInExperiment),
                        flagKeyName,
                        flagName))
                .orElse(null);
    }

    private boolean isSendToExperiment(String user,
                                       DataModel.RolloutVariation rollout,
                                       Boolean exptIncludeAllRules,
                                       Boolean ruleIncludedInExperiment) {
        if (exptIncludeAllRules) {
            return true;
        }
        if (ruleIncludedInExperiment) {
            double sendToExperimentPercentage = rollout.getExptRollout();
            double splittingPercentage = rollout.splittingPercentage();
            if (sendToExperimentPercentage == 0D || splittingPercentage == 0D) {
                return false;
            }
            double upperBound = sendToExperimentPercentage / splittingPercentage;
            if (upperBound > 1D) {
                upperBound = 1D;
            }
            return VariationSplittingAlgorithm.ifKeyBelongsPercentage(user, new double[]{0D, upperBound});
        }
        return false;
    }


}
