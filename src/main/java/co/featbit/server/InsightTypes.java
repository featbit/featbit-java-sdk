package co.featbit.server;

import co.featbit.commons.model.FBUser;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class InsightTypes {

    public static abstract class Event {
        protected final FBUser user;

        Event(FBUser user) {
            this.user = user;
        }

        public FBUser getUser() {
            return user;
        }

        public abstract boolean isSendEvent();

        public abstract Event add(Object element);
    }

    final static class NullEvent extends Event {
        static final NullEvent INSTANCE = new NullEvent();

        private NullEvent() {
            super(null);
        }

        @Override
        public boolean isSendEvent() {
            return false;
        }

        @Override
        public Event add(Object element) {
            return null;
        }
    }

    @JsonAdapter(UserEventSerializer.class)
    final static class UserEvent extends Event {
        private UserEvent(FBUser user) {
            super(user);
        }

        static UserEvent of(FBUser user) {
            return new UserEvent(user);
        }

        @Override
        public boolean isSendEvent() {
            return user != null;
        }

        @Override
        public Event add(Object element) {
            return this;
        }
    }

    @JsonAdapter(FlagEventSerializer.class)
    final static class FlagEvent extends Event {
        private final List<FlagEventVariation> userVariations = new ArrayList<>();

        private FlagEvent(FBUser user) {
            super(user);
        }

        static FlagEvent of(FBUser user) {
            return new FlagEvent(user);
        }

        @Override
        public Event add(Object element) {
            FlagEventVariation variation = (FlagEventVariation) element;
            if (variation != null && !variation.getVariation().getIndex().equals(Evaluator.NO_EVAL_RES)) {
                userVariations.add(variation);
            }
            return this;
        }

        @Override
        public boolean isSendEvent() {
            return user != null && !userVariations.isEmpty();
        }

        public void updateTimeStamp(){
            userVariations.forEach(FlagEventVariation::updateTimestamp);
        }
    }

    @JsonAdapter(MetricEventSerializer.class)
    final static class MetricEvent extends Event {
        private final List<Metric> metrics = new ArrayList<>();

        MetricEvent(FBUser user) {
            super(user);
        }

        static MetricEvent of(FBUser user) {
            return new MetricEvent(user);
        }

        @Override
        public boolean isSendEvent() {
            return user != null && !metrics.isEmpty();
        }

        @Override
        public Event add(Object element) {
            Metric metric = (Metric) element;
            if (metric != null) {
                metrics.add(metric);
            }
            return this;
        }
    }

    static final class FlagEventVariation {
        private final String featureFlagKeyName;
        private long timestamp;
        private final Evaluator.EvalResult variation;

        FlagEventVariation(String featureFlagKeyName, long timestamp, Evaluator.EvalResult variation) {
            this.featureFlagKeyName = featureFlagKeyName;
            this.timestamp = timestamp;
            this.variation = variation;
        }

        static FlagEventVariation of(String featureFlagKeyName, Evaluator.EvalResult variation) {
            return new FlagEventVariation(featureFlagKeyName, Instant.now().toEpochMilli(), variation);
        }

        public String getFeatureFlagKeyName() {
            return featureFlagKeyName;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void updateTimestamp() {
            this.timestamp = Instant.now().toEpochMilli();
        }

        public Evaluator.EvalResult getVariation() {
            return variation;
        }
    }

    static final class Metric {
        private final String route = "index/metric";
        private final String type = "CustomEvent";
        private final String eventName;
        private final Double numericValue;
        private final String appType = "javaserverside";
        private final long timestamp;

        Metric(String eventName, Double numericValue, long timestamp) {
            this.eventName = eventName;
            this.numericValue = numericValue;
            this.timestamp = timestamp;
        }

        static Metric of(String eventName, Double numericValue) {
            return new Metric(eventName,
                    numericValue == null ? 1.0D : numericValue,
                    Instant.now().toEpochMilli());
        }

        public String getEventName() {
            return eventName;
        }

        public Double getNumericValue() {
            return numericValue;
        }

        public String getRoute() {
            return route;
        }

        public String getType() {
            return type;
        }

        public String getAppType() {
            return appType;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    final static class UserEventSerializer implements JsonSerializer<UserEvent> {
        @Override
        public JsonElement serialize(UserEvent userEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            return serializeUser(userEvent.getUser());
        }
    }

    final static class FlagEventSerializer implements JsonSerializer<FlagEvent> {

        @Override
        public JsonElement serialize(FlagEvent flagEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            FBUser user = flagEvent.getUser();
            JsonObject json = serializeUser(user);
            JsonArray array1 = new JsonArray();
            for (FlagEventVariation variation : flagEvent.userVariations) {
                JsonObject var = new JsonObject();
                var.addProperty("featureFlagKey", variation.getFeatureFlagKeyName());
                var.addProperty("sendToExperiment", variation.getVariation().isSendToExperiment());
                var.addProperty("timestamp", variation.getTimestamp());
                JsonObject v = new JsonObject();
                v.addProperty("id", variation.getVariation().getIndex());
                v.addProperty("value", variation.getVariation().getValue());
                v.addProperty("reason", variation.getVariation().getReason());
                var.add("variation", v);
                array1.add(var);
            }
            json.add("variations", array1);
            return json;
        }
    }

    final static class MetricEventSerializer implements JsonSerializer<MetricEvent> {
        @Override
        public JsonElement serialize(MetricEvent metricEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            FBUser user = metricEvent.getUser();
            JsonObject json = serializeUser(user);
            JsonArray array1 = new JsonArray();
            for (Metric metric : metricEvent.metrics) {
                JsonObject var = new JsonObject();
                var.addProperty("route", metric.getRoute());
                var.addProperty("type", metric.getType());
                var.addProperty("eventName", metric.getEventName());
                var.addProperty("numericValue", metric.getNumericValue());
                var.addProperty("appType", metric.getAppType());
                var.addProperty("timestamp", metric.getTimestamp());
                array1.add(var);
            }
            json.add("metrics", array1);
            return json;
        }
    }

    private static JsonObject serializeUser(FBUser user) {
        JsonObject json = new JsonObject();
        JsonObject json1 = new JsonObject();
        json1.addProperty("name", user.getUserName());
        json1.addProperty("keyId", user.getKey());
        JsonArray array = new JsonArray();
        for (Map.Entry<String, String> keyItem : user.getCustom().entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", keyItem.getKey());
            p.addProperty("value", keyItem.getValue());
            array.add(p);
        }
        json1.add("customizedProperties", array);
        json.add("user", json1);
        return json;
    }

    enum InsightMessageType {
        FLAGS, FLUSH, SHUTDOWN, METRICS, USERS, STATISTICS
    }

    static final class InsightMessage {
        private final InsightMessageType type;
        private final Event event;
        private final Object waitLock;

        // waitLock is initialized only when you need to wait until the message is completely handled
        // Ex, shutdown, in this case, we should to wait until all events are sent to server
        InsightMessage(InsightMessageType type, Event event, boolean awaitToComplete) {
            this.type = type;
            this.event = event;
            // permit = 0, so wait until a permit releases
            this.waitLock = awaitToComplete ? new Object() : null;
        }

        public void completed() {
            if (waitLock != null) {
                synchronized (waitLock) {
                    waitLock.notifyAll();
                }
            }
        }

        public void waitForComplete() {
            if (waitLock == null) {
                return;
            }
            while (true) {
                synchronized (waitLock) {
                    try {
                        waitLock.wait();
                        return;
                    } catch (InterruptedException ignore) {
                    }
                }
            }

        }

        public InsightMessageType getType() {
            return type;
        }

        public Event getEvent() {
            return event;
        }
    }

}
