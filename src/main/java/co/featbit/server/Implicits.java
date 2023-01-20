package co.featbit.server;

import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

abstract class Implicits {
    static final class ComplexAllFlagStates extends AllFlagStates {

        private transient final Consumer<InsightTypes.Event> eventHandler;

        private transient final Map<EvalDetail<String>, InsightTypes.Event> complexData;


        ComplexAllFlagStates(boolean success,
                             String message,
                             Map<EvalDetail<String>, InsightTypes.Event> complexData,
                             Consumer<InsightTypes.Event> eventHandler) {
            super(success, message, complexData == null ? null : new ArrayList<>(complexData.keySet()));
            this.complexData = complexData;
            this.eventHandler = eventHandler;
        }

        private void sendEvent(String flagKeyName) {
            EvalDetail<String> ed = cache.get(flagKeyName);
            if (ed != null && eventHandler != null && complexData != null) {
                InsightTypes.Event event = complexData.get(ed);
                eventHandler.accept(event);
            }
        }

        @Override
        public EvalDetail<String> getStringDetail(String flagKeyName, String defaultValue) {
            EvalDetail<String> ed = super.getStringDetail(flagKeyName, defaultValue);
            sendEvent(flagKeyName);
            return ed;
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
            EvalDetail<Boolean> ed = super.getBooleanDetail(flagKeyName, defaultValue);
            sendEvent(flagKeyName);
            return ed;
        }

        @Override
        public Integer getInteger(String flagKeyName, Integer defaultValue) {
            return this.getIntegerDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Integer> getIntegerDetail(String flagKeyName, Integer defaultValue) {
            EvalDetail<Integer> ed = super.getIntegerDetail(flagKeyName, defaultValue);
            sendEvent(flagKeyName);
            return ed;
        }

        @Override
        public Long getLong(String flagKeyName, Long defaultValue) {
            return this.getLongDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Long> getLongDetail(String flagKeyName, Long defaultValue) {
            EvalDetail<Long> ed = super.getLongDetail(flagKeyName, defaultValue);
            sendEvent(flagKeyName);
            return ed;
        }

        @Override
        public Double getDouble(String flagKeyName, Double defaultValue) {
            return this.getDoubleDetail(flagKeyName, defaultValue).getVariation();
        }

        @Override
        public EvalDetail<Double> getDoubleDetail(String flagKeyName, Double defaultValue) {
            EvalDetail<Double> ed = super.getDoubleDetail(flagKeyName, defaultValue);
            sendEvent(flagKeyName);
            return ed;
        }

        @Override
        public <T> T getJsonObject(String flagKeyName, T defaultValue, Class<T> clazz) {
            return this.getJsonDetail(flagKeyName, defaultValue, clazz).getVariation();
        }

        @Override
        public <T> EvalDetail<T> getJsonDetail(String flagKeyName, T defaultValue, Class<T> clazz) {
            EvalDetail<T> ed = super.getJsonDetail(flagKeyName, defaultValue, clazz);
            sendEvent(flagKeyName);
            return ed;
        }
    }
}
