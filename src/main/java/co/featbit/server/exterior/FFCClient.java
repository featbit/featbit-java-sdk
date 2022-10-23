package co.featbit.server.exterior;

import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.FFCUser;
import co.featbit.commons.model.FlagState;
import co.featbit.commons.model.UserTag;
import co.featbit.server.Status;

import java.io.Closeable;
import java.util.List;
import java.util.Map;


/**
 * This interface defines the public methods of {@link co.featbit.server.FFCClientImp}.
 * <p>
 * Applications will normally interact directly with {@link co.featbit.server.FFCClientImp}
 * and must use its constructor to initialize the SDK.
 */
public interface FFCClient extends Closeable {
    /**
     * Tests whether the client is ready to be used.
     *
     * @return true if the client is ready, or false if it is still initializing
     */
    boolean isInitialized();

    /**
     * Calculates the value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    String variation(String featureFlagKey, FFCUser user, String defaultValue);

    /**
     * Calculates the boolean value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return if the flag should be enabled, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue);

    /**
     * alias of boolVariation for a given user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @return if the flag should be enabled, or false if the flag is disabled, or an error occurs
     */
    boolean isEnabled(String featureFlagKey, FFCUser user);

    /**
     * Calculates the double value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    double doubleVariation(String featureFlagKey, FFCUser user, Double defaultValue);

    /**
     * Calculates the integer value of a feature flag for a given user.
     * Note that If the variation has a numeric value, but not an integer, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    int intVariation(String featureFlagKey, FFCUser user, Integer defaultValue);

    /**
     * Calculates the long value of a feature flag for a given user.
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    long longVariation(String featureFlagKey, FFCUser user, Long defaultValue);

    /**
     * Calculates the json value of a feature flag for a given user.
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param clazz          json deserialization class
     * @param defaultValue   the default value of the flag
     * @param <T>            json object type
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled, current user doesn't exist
     */
    <T> T jsonVariation(String featureFlagKey, FFCUser user, Class<T> clazz, T defaultValue);

    /**
     * Returns true if the specified feature flag currently exists.
     *
     * @param featureKey the unique key for the feature flag
     * @return true if the flag exists
     */
    boolean isFlagKnown(String featureKey);

    /**
     * Returns an interface for tracking the status of the update processor.
     * <p>
     * The update processor is the mechanism that the SDK uses to get feature flag, such as a
     * streaming connection. The {@link co.featbit.server.Status.DataUpdateStatusProvider}
     * is used to check whether the update processor is currently operational
     *
     * @return a {@link co.featbit.server.Status.DataUpdateStatusProvider}
     */
    Status.DataUpdateStatusProvider getDataUpdateStatusProvider();

    /**
     * initialization in the offline mode
     * <p>
     *
     * @param json feature flags in the json format
     * @return true if the initialization is well done
     * @throws co.featbit.commons.json.JsonParseException if json is invalid
     */
    boolean initializeFromExternalJson(String json);

    /**
     * Returns a list of all feature flags value with details for a given user, including the reason
     * that describes the way the value was determined, that can be used on the client side sdk or a front end .
     * <p>
     * note that this method does not send insight events back to featureflag.co.
     *
     * @param user the end user requesting the flag
     * @return a {@link AllFlagStates}
     */
    AllFlagStates<String> getAllLatestFlagsVariations(FFCUser user);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<String> variationDetail(String featureFlagKey, FFCUser user, String defaultValue);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Boolean> boolVariationDetail(String featureFlagKey, FFCUser user, Boolean defaultValue);

    /**
     * Calculates the double value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Double> doubleVariationDetail(String featureFlagKey, FFCUser user, Double defaultValue);

    /**
     * Calculates the int value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a int value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Integer> intVariationDetail(String featureFlagKey, FFCUser user, Integer defaultValue);

    /**
     * Calculates the long value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Long> longVariationDetail(String featureFlagKey, FFCUser user, Long defaultValue);

    /**
     * Calculates the json value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param clazz          json deserialization class
     * @param defaultValue   the default value of the flag
     * @param <T>            json object type
     * @return an {@link FlagState} object
     */
    <T> FlagState<T> jsonVariationDetail(String featureFlagKey, FFCUser user, Class<T> clazz, T defaultValue);

    /**
     * Flushes all pending events.
     */
    void flush();

    /**
     * tracks that a user performed an event and provides a default numeric value for custom metrics
     *
     * @param user      the user that performed the event
     * @param eventName the name of the event
     */
    void trackMetric(FFCUser user, String eventName);

    /**
     * tracks that a user performed an event, and provides an additional numeric value for custom metrics.
     *
     * @param user        the user that performed the event
     * @param eventName   the name of the event
     * @param metricValue a numeric value used by the experimentation feature in numeric custom metrics.
     */
    void trackMetric(FFCUser user, String eventName, double metricValue);

    /**
     * tracks that a user performed a series of events with default numeric value for custom metrics
     *
     * @param user       the user that performed the event
     * @param eventNames event names
     */
    void trackMetrics(FFCUser user, String... eventNames);

    /**
     * tracks that a user performed a series of events
     *
     * @param user    the user that performed the event
     * @param metrics event name and numeric value in K/V
     */
    void trackMetrics(FFCUser user, Map<String, Double> metrics);
}
