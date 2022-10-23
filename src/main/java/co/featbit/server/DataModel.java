package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.server.exterior.DataStoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.JsonAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class DataModel {

    private DataModel() {
    }

    /**
     * the object is an implementation of{@link DataStoreTypes.Item}, to represent the archived data
     */
    public final static class ArchivedItem implements DataStoreTypes.Item {
        private final String id;
        private final Long timestamp;
        private final Boolean isArchived = Boolean.TRUE;

        public ArchivedItem(String id, Long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_ARCHIVED_ITEM;
        }
    }

    static class StreamingMessage {
        static final String DATA_SYNC = "data-sync";
        static final String PING = "ping";

        protected final String messageType;

        StreamingMessage(String messageType) {
            this.messageType = messageType;
        }

        public String getMessageType() {
            return messageType;
        }
    }

    static class DataSyncMessage extends StreamingMessage {
        final InternalData data;

        DataSyncMessage(Long timestamp) {
            super(timestamp == null ? PING : DATA_SYNC);
            this.data = timestamp == null ? null : new InternalData(timestamp);
        }

        static class InternalData {
            Long timestamp;

            InternalData(Long timestamp) {
                this.timestamp = timestamp;
            }
        }
    }

    static class All extends StreamingMessage {
        private final Data data;

        All(String messageType, Data data) {
            super(messageType);
            this.data = data;
        }

        public Data data() {
            return data;
        }

        boolean isProcessData() {
            return DATA_SYNC.equalsIgnoreCase(messageType) && data != null && ("full".equalsIgnoreCase(data.eventType) || "patch".equalsIgnoreCase(data.eventType));
        }
    }

    /**
     * versioned data of feature flags and related data from featureflag.co
     */
    @JsonAdapter(JsonHelper.AfterJsonParseDeserializableTypeAdapterFactory.class)
    static class Data implements JsonHelper.AfterJsonParseDeserializable {

        private final String eventType;
        private final List<FeatureFlag> featureFlags;
        private final List<Segment> segments;
        private Long timestamp;

        Data(String eventType, List<FeatureFlag> featureFlags, List<Segment> segments) {
            this.eventType = eventType;
            this.featureFlags = featureFlags;
            this.segments = segments;
        }

        @Override
        public void afterDeserialization() {
            long v1 = (featureFlags != null) ? featureFlags.stream().map(flag -> flag.timestamp).max(Long::compare).orElse(0L) : 0L;
            long v2 = (segments != null) ? segments.stream().map(segment -> segment.timestamp).max(Long::compare).orElse(0L) : 0L;
            timestamp = Math.max(v1, v2);
        }

        public List<FeatureFlag> getFeatureFlags() {
            return featureFlags == null ? Collections.emptyList() : featureFlags;
        }

        public List<Segment> getSegments() {
            return segments == null ? Collections.emptyList() : segments;
        }

        public String getEventType() {
            return eventType;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> toStorageType() {
            ImmutableMap.Builder<String, DataStoreTypes.Item> flags = ImmutableMap.builder();
            for (FeatureFlag flag : getFeatureFlags()) {
                DataStoreTypes.Item item = flag.isArchived ? flag.toArchivedItem() : flag;
                flags.put(item.getId(), item);
            }
            ImmutableMap.Builder<String, DataStoreTypes.Item> segments = ImmutableMap.builder();
            for (Segment segment : getSegments()) {
                DataStoreTypes.Item item = segment.isArchived ? segment.toArchivedItem() : segment;
                segments.put(item.getId(), item);
            }
            return ImmutableMap.of(DataStoreTypes.FEATURES, flags.build(), DataStoreTypes.SEGMENTS, segments.build());
        }
    }

    static class Segment implements DataStoreTypes.Item {

        private final String id;

        private final Boolean isArchived;

        private final Long timestamp;

        private final List<String> included;

        private final List<String> excluded;

        private final List<TargetRule> rules;

        Segment(String id, Boolean isArchived, Long timestamp, List<String> included, List<String> excluded, List<TargetRule> rules) {
            this.id = id;
            this.isArchived = isArchived;
            this.timestamp = timestamp;
            this.included = included;
            this.excluded = excluded;
            this.rules = rules;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived != null && isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_SEGMENT;
        }

        public List<String> getIncluded() {
            return included == null ? Collections.emptyList() : included;
        }

        public List<String> getExcluded() {
            return excluded == null ? Collections.emptyList() : excluded;
        }

        public List<TargetRule> getRules() {
            return rules == null ? Collections.emptyList() : rules;
        }

        public Boolean isMatchUser(String userKeyId) {
            if (getExcluded().contains(userKeyId)) {
                return Boolean.FALSE;
            }

            if (getIncluded().contains(userKeyId)) {
                return Boolean.TRUE;
            }
            // if no included or excluded, then it's to match rules
            return null;
        }

        public DataStoreTypes.Item toArchivedItem() {
            return new ArchivedItem(this.id, this.timestamp);
        }
    }

    @JsonAdapter(JsonHelper.AfterJsonParseDeserializableTypeAdapterFactory.class)
    static class FeatureFlag implements DataStoreTypes.Item, JsonHelper.AfterJsonParseDeserializable {
        final String id;
        private final Long timestamp;
        private final boolean isArchived;
        private final boolean exptIncludeAllTargets;
        private final boolean isEnabled;
        private final String name;
        private final String key;
        private final String variationType;
        private final List<Variation> variations;
        public final List<TargetUser> targetUsers;
        private final List<TargetRule> rules;
        private final Fallthrough fallthrough;
        private final String disabledVariationId;
        private transient Map<String, Variation> variationMap;

        FeatureFlag(String id, Long timestamp, boolean isArchived, boolean exptIncludeAllTargets, boolean isEnabled, String name, String key, String variationType, List<Variation> variations, List<TargetUser> targetUsers, List<TargetRule> rules, Fallthrough fallthrough, String disabledVariationId) {
            this.id = id;
            this.timestamp = timestamp;
            this.isArchived = isArchived;
            this.exptIncludeAllTargets = exptIncludeAllTargets;
            this.isEnabled = isEnabled;
            this.name = name;
            this.key = key;
            this.variationType = variationType;
            this.variations = variations;
            this.targetUsers = targetUsers;
            this.rules = rules;
            this.fallthrough = fallthrough;
            this.disabledVariationId = disabledVariationId;
        }

        public DataStoreTypes.Item toArchivedItem() {
            return new ArchivedItem(this.key, this.timestamp);
        }

        @Override
        public String getId() {
            return key;
        }

        @Override
        public boolean isArchived() {
            return isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_FEATURE_FLAG;
        }

        public boolean exptIncludeAllTargets() {
            return exptIncludeAllTargets;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public List<Variation> getVariations() {
            return variations == null ? Collections.emptyList() : variations;
        }

        public Variation getVariation(String id) {
            return variationMap.get(id);
        }

        public List<TargetUser> getTargetUsers() {
            return targetUsers == null ? Collections.emptyList() : targetUsers;
        }

        public List<TargetRule> getRules() {
            return rules == null ? Collections.emptyList() : rules;
        }

        public Fallthrough getFallthrough() {
            return fallthrough;
        }

        public String getDisabledVariationId() {
            return disabledVariationId;
        }

        public String getName() {
            return name;
        }

        public String getKey() {
            return key;
        }

        public String getVariationType() {
            return variationType;
        }

        @Override
        public void afterDeserialization() {
            if (!isArchived) {
                ImmutableMap.Builder<String, Variation> builder = ImmutableMap.builder();
                for (Variation variation : getVariations()) {
                    builder.put(variation.id, variation);
                }
                this.variationMap = builder.build();
            }
        }
    }

    static final class Variation {
        private final String id;
        private final String value;

        Variation(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public String getValue() {
            return value;
        }
    }

    static final class TargetUser {
        private final List<String> keyIds;

        private final String variationId;

        public TargetUser(List<String> keyIds, String variationId) {
            this.keyIds = keyIds;
            this.variationId = variationId;
        }

        public List<String> getKeyIds() {
            return keyIds == null ? Collections.emptyList() : keyIds;
        }

        public String getVariationId() {
            return variationId;
        }

        public boolean isTargeted(String user) {
            return getKeyIds().stream().anyMatch(key -> key.equals(user));
        }
    }

    static final class TargetRule {
        private final boolean includedInExpt;

        private final List<Condition> conditions;

        private final List<RolloutVariation> variations;

        TargetRule(boolean includedInExpt, List<Condition> conditions, List<RolloutVariation> variations) {
            this.includedInExpt = includedInExpt;
            this.conditions = conditions;
            this.variations = variations;
        }

        public boolean includedInExpt() {
            return includedInExpt;
        }

        public List<Condition> getConditions() {
            return conditions == null ? Collections.emptyList() : conditions;
        }

        public List<RolloutVariation> getVariations() {
            return variations == null ? Collections.emptyList() : variations;
        }
    }

    static final class Condition {
        private final String property;

        private final String op;

        private final String value;

        Condition(String property, String op, String value) {
            this.property = property;
            this.op = op;
            this.value = value;
        }

        public String getProperty() {
            return property;
        }

        public String getOp() {
            return op;
        }

        public String getValue() {
            return value;
        }
    }

    static final class RolloutVariation {
        private final String id;

        private final double[] rollout;

        private final double exptRollout;

        RolloutVariation(String id, double[] rollout, double exptRollout) {
            this.id = id;
            this.rollout = rollout;
            this.exptRollout = exptRollout;
        }

        public String getId() {
            return id;
        }

        public double[] getRollout() {
            return rollout;
        }

        public double getExptRollout() {
            return exptRollout;
        }

        public double splittingPercentage() {
            if (rollout != null) return rollout[1] - rollout[0];
            return 0D;
        }
    }

    static final class Fallthrough {
        private final boolean includedInExpt;

        private final List<RolloutVariation> variations;

        public Fallthrough(boolean includedInExpt, List<RolloutVariation> variations) {
            this.includedInExpt = includedInExpt;
            this.variations = variations;
        }

        public boolean includedInExpt() {
            return includedInExpt;
        }

        public List<RolloutVariation> getVariations() {
            return variations == null ? Collections.emptyList() : variations;
        }
    }


}
