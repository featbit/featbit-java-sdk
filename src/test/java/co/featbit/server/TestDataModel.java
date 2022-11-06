package co.featbit.server;

import co.featbit.server.exterior.DataStoreTypes;
import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

abstract class TestDataModel {
    static class TestItem implements DataStoreTypes.Item, Serializable {
        private String id = UUID.randomUUID().toString();

        private final boolean isArchived;

        private final long timestamp = Instant.now().toEpochMilli();

        private final String value;

        TestItem(boolean isArchived, String value) {
            this.isArchived = isArchived;
            this.value = value;
        }

        TestItem(String id, boolean isArchived, String value) {
            this.id = id;
            this.isArchived = isArchived;
            this.value = value;
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
            return FFC_TEST_ITEM;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestItem testItem = (TestItem) o;
            return isArchived == testItem.isArchived && timestamp == testItem.timestamp && Objects.equals(id, testItem.id) && Objects.equals(value, testItem.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, isArchived, timestamp, value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("id", id).add("isArchived", isArchived).add("timestamp", timestamp).add("value", value).toString();
        }

        @Override
        public int compareTo(@NotNull DataStoreTypes.Item o) {
            return (int) (timestamp - o.getTimestamp());
        }
    }
}
