package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.json.JsonParseException;
import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.BooleanUtils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import static co.featbit.server.Evaluator.*;

abstract class Implicits {
    static final class ComplexAllFlagStates implements AllFlagStates {

        private boolean success;

        private String reason;

        private transient final Consumer<InsightTypes.Event> eventHandler;

        private transient final Map<String, Map.Entry<Evaluator.EvalResult, InsightTypes.FlagEvent>> cache;


        ComplexAllFlagStates(boolean success,
                             String reason,
                             Map<Evaluator.EvalResult, InsightTypes.FlagEvent> data,
                             Consumer<InsightTypes.Event> eventHandler) {
            this.success = success;
            this.reason = reason;
            this.eventHandler = eventHandler;
            ImmutableMap.Builder<String, Map.Entry<Evaluator.EvalResult, InsightTypes.FlagEvent>> builder = ImmutableMap.builder();
            data.forEach((evalResult, event) -> builder.put(evalResult.getKeyName(), new AbstractMap.SimpleImmutableEntry<>(evalResult, event)));
            cache = builder.build();
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        @Override
        public String getReason() {
            return reason;
        }

        private Evaluator.EvalResult getInternal(String flagKeyName, Object defaultValue, Class<?> requiredType) {
            Map.Entry<Evaluator.EvalResult, InsightTypes.FlagEvent> entry = cache.get(flagKeyName);
            if (entry == null) {
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND, flagKeyName, FLAG_NAME_UNKNOWN);
            }
            Evaluator.EvalResult er = entry.getKey();
            if (Utils.checkType(er.getFlagType(), requiredType, defaultValue.toString())) {
                if (eventHandler != null) {
                    InsightTypes.FlagEvent event = entry.getValue();
                    event.updateTimeStamp();
                    eventHandler.accept(event);
                }
                return er;
            }
            return Evaluator.EvalResult.error(defaultValue.toString(), REASON_WRONG_TYPE, er.getKeyName(), er.getName());
        }

        @Override
        public EvalDetail<String> getStringDetail(String flagKeyName, String defaultValue) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, String.class);
            return er.toEvalDetail(er.getValue());
        }

        @Override
        public String getString(String flagKeyName, String defaultValue) {
            return this.getStringDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public Boolean getBoolean(String flagKeyName, Boolean defaultValue) {
            return this.getBooleanDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Boolean> getBooleanDetail(String flagKeyName, Boolean defaultValue) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, Boolean.class);
            return er.toEvalDetail(BooleanUtils.toBoolean(er.getValue()));
        }

        @Override
        public Integer getInteger(String flagKeyName, Integer defaultValue) {
            return this.getIntegerDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Integer> getIntegerDetail(String flagKeyName, Integer defaultValue) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, Integer.class);
            return er.toEvalDetail(Double.valueOf(er.getValue()).intValue());
        }

        @Override
        public Long getLong(String flagKeyName, Long defaultValue) {
            return this.getLongDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Long> getLongDetail(String flagKeyName, Long defaultValue) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, Long.class);
            return er.toEvalDetail(Double.valueOf(er.getValue()).longValue());
        }

        @Override
        public Double getDouble(String flagKeyName, Double defaultValue) {
            return this.getDoubleDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Double> getDoubleDetail(String flagKeyName, Double defaultValue) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, Double.class);
            return er.toEvalDetail(Double.valueOf(er.getValue()));
        }

        @Override
        public <T> T getJsonObject(String flagKeyName, T defaultValue, Class<T> clazz) {
            return this.getJsonDetail(flagKeyName, defaultValue, clazz).getVariation();
        }

        @Override
        public <T> EvalDetail<T> getJsonDetail(String flagKeyName, T defaultValue, Class<T> clazz) {
            Evaluator.EvalResult er = getInternal(flagKeyName, defaultValue, clazz);
            T value = Utils.parseJsonObject(er.getValue(), defaultValue, clazz, DEFAULT_JSON_VALUE.equals(er.getValue()));
            return er.toEvalDetail(value);
        }
    }
}
