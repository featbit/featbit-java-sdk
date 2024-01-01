package co.featbit.server.exterior;

/**
 * An event listener that is notified when a feature flag's value has changed for a specific flag key and a {@link co.featbit.commons.model.FBUser}.
 * @see FlagTracker
 */
public interface FlagValueChangeListener {
    /**
     * The SDK calls this method when a feature flag's value has changed for a specific flag key and a {@link co.featbit.commons.model.FBUser}.
     *
     * @param event The {@link FlagValueChangeEvent} that contains the flag key, the old value and the new value
     */
    void onFlagValueChange(FlagValueChangeEvent event);
}
