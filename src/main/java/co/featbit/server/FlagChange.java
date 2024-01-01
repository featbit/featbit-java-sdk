package co.featbit.server;

import java.util.Objects;

public abstract class FlagChange {

    public static class FlagChangeEvent {
        private final String key;

        public FlagChangeEvent(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagChangeEvent that = (FlagChangeEvent) o;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }

    public interface FlagChangeListener {
        void onFlagChange(FlagChangeEvent event);
    }

}
