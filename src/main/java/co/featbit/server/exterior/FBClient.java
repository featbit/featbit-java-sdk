package co.featbit.server.exterior;

import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import co.featbit.server.FBClientImp;
import co.featbit.server.Status;

import java.io.Closeable;
import java.util.Map;


/**
 * This interface defines the public methods of {@link FBClientImp}.
 * <p>
 * Applications will normally interact directly with {@link FBClientImp}
 * and must use its constructor to initialize the SDK.
 */
public interface FBClient extends Closeable {
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
    String variation(String featureFlagKey, FBUser user, String defaultValue);

    /**
     * Calculates the boolean value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return if the flag should be enabled, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    boolean boolVariation(String featureFlagKey, FBUser user, Boolean defaultValue);

    /**
     * Calculates the double value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    double doubleVariation(String featureFlagKey, FBUser user, Double defaultValue);

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
    int intVariation(String featureFlagKey, FBUser user, Integer defaultValue);

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
    long longVariation(String featureFlagKey, FBUser user, Long defaultValue);

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
    <T> T jsonVariation(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue);

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
     * note that this method does not send insight events back to feature flag center.
     *
     * @param user the end user requesting the flag
     * @return a {@link AllFlagStates}
     */
    AllFlagStates getAllLatestFlagsVariations(FBUser user);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * Note that this method does not cast the result of flag evaluation, any flag value is a string type
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<String> variationDetail(String featureFlagKey, FBUser user, String defaultValue);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Boolean> boolVariationDetail(String featureFlagKey, FBUser user, Boolean defaultValue);

    /**
     * Calculates the double value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Double> doubleVariationDetail(String featureFlagKey, FBUser user, Double defaultValue);

    /**
     * Calculates the int value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not an int value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Integer> intVariationDetail(String featureFlagKey, FBUser user, Integer defaultValue);

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
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Long> longVariationDetail(String featureFlagKey, FBUser user, Long defaultValue);

    /**
     * Calculates the json value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param clazz          json deserialization class
     * @param defaultValue   the default value of the flag
     * @param <T>            json object type
     * @return an {@link EvalDetail} object
     */
    <T> EvalDetail<T> jsonVariationDetail(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue);

    /**
     * Flushes all pending events.
     */
    void flush();

    /**
     * register a user
     *
     * @param user user to register
     */
    void identify(FBUser user);

    /**
     * tracks that a user performed an event and provides a default numeric value for custom metrics
     *
     * @param user      the user that performed the event
     * @param eventName the name of the event
     */
    void trackMetric(FBUser user, String eventName);

    /**
     * tracks that a user performed an event, and provides an additional numeric value for custom metrics.
     *
     * @param user        the user that performed the event
     * @param eventName   the name of the event
     * @param metricValue a numeric value used by the experimentation feature in numeric custom metrics.
     */
    void trackMetric(FBUser user, String eventName, double metricValue);

    /**
     * tracks that a user performed a series of events with default numeric value for custom metrics
     *
     * @param user       the user that performed the event
     * @param eventNames event names
     */
    void trackMetrics(FBUser user, String... eventNames);

    /**
     * tracks that a user performed a series of events
     *
     * @param user    the user that performed the event
     * @param metrics event name and numeric value in K/V
     */
    void trackMetrics(FBUser user, Map<String, Double> metrics);
}
