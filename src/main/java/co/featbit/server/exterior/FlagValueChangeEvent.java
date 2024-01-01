package co.featbit.server.exterior;

import co.featbit.server.FlagChange;

import java.util.Objects;

/**
 * An event that is sent to {@link FlagValueChangeListener} when a feature flag's value has changed for a specific flag key and a {@link co.featbit.commons.model.FBUser}.
 */
public class FlagValueChangeEvent extends FlagChange.FlagChangeEvent {
    private final Object oldValue;
    private final Object newValue;

    /**
     * Constructs a new instance.
     *
     * @param key      the feature flag key
     * @param oldValue the previous value of the feature flag
     * @param newValue the new value of the feature flag
     */
    public FlagValueChangeEvent(String key, Object oldValue, Object newValue) {
        super(key);
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FlagValueChangeEvent that = (FlagValueChangeEvent) o;
        return Objects.equals(oldValue, that.oldValue) && Objects.equals(newValue, that.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), oldValue, newValue);
    }
}
