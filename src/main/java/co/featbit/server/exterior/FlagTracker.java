package co.featbit.server.exterior;

import co.featbit.commons.model.FBUser;
import co.featbit.server.FlagChange;

/**
 * A registry to register the flag change listeners in order to track changes in feature flag configurations.
 * <p>
 * The registered listeners only work if the SDK is actually connecting to FeatBit feature flag center.
 * If the SDK is only in offline mode then it cannot know when there is a change, because flags are read on an as-needed basis.
 * <p>
 * Application code never needs to initialize or extend this class directly.
 */
public interface FlagTracker {
    /**
     * Registers a listener to be notified of a change in a specific feature flag's value for a specific FeatBit user.
     * <p>
     * When you call this method, it evaluates immediately the feature flag. It then registers a
     * {@link co.featbit.server.FlagChange.FlagChangeListener} to start listening for feature flag configuration
     * changes, and whenever the specified feature flag or user segment changes, it evaluates the flag for the {@link FBUser}.
     * It then calls your {@link FlagValueChangeListener} if and only if the resulting value has changed.
     * <p>
     * The returned {@link co.featbit.server.FlagChange.FlagChangeListener} represents the subscription that was created by this method
     * call; to unsubscribe, pass that object (not your {@code FlagValueChangeListener}) to
     * {@link #removeFlagChangeListener(co.featbit.server.FlagChange.FlagChangeListener)}.
     *
     * @param flagKey  The key of the feature flag to track
     * @param user     The {@link FBUser} to evaluate the flag value
     * @param listener The {@link FlagValueChangeListener} to be notified when the flag value changes
     * @return The {@link co.featbit.server.FlagChange.FlagChangeListener} that was registered
     */
    FlagChange.FlagChangeListener addFlagValueChangeListener(String flagKey, FBUser user, FlagValueChangeListener listener);

    /**
     * Unregisters a listener so that it will no longer be notified of feature flag changes.
     *
     * @param listener the {@link FlagChange.FlagChangeListener} to unregister
     */
    void removeFlagChangeListener(FlagChange.FlagChangeListener listener);

    /**
     * Registers a listener to be notified of feature flag changes in general.
     * <p>
     * The listener will be notified whenever the SDK receives any change to any feature flag's configuration,
     * or to a user segment that is referenced by a feature flag.
     * <p>
     * Note that this does not necessarily mean the flag's value has changed for any particular user,
     * only that some part of the flag configuration was changed so that it <i>may</i> return a
     * different value than it previously returned for some user. If you want to track flag value changes,
     * use {@link #addFlagValueChangeListener(String, FBUser, FlagValueChangeListener)} instead.
     * <p>
     * The registered listeners only work if the SDK is actually connecting to FeatBit feature flag center.
     *
     * @param listener the {@link FlagChange.FlagChangeListener} to register
     */
    void addFlagChangeListener(FlagChange.FlagChangeListener listener);
}
