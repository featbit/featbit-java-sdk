package co.featbit.commons.model;

import java.util.Collection;

/**
 * The interface provides a standard return responding the request of getting all flag values from a client sdk
 */
public interface AllFlagStates {

    /**
     * if the last evaluation is successful
     *
     * @return true if the evaluation is successful
     */
    boolean isSuccess();

    /**
     * return the reason of last evaluation
     *
     * @return OK if the last evaluation is successful, otherwise return the reason
     */
    String getReason();

    /**
     * return the flag keys of all flags in the specified Environment
     *
     * @return a collection of flag keys
     */
    Collection<String> getFlagKeys();

    /**
     * return the string value of a given flag key name or default value if flag not existed
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return string value or default value if flag not existed
     */
    String getString(String flagKeyName, String defaultValue);

    /**
     * returns an object that describes the way the string flag value was determined.
     * Note that default value in the detail if flag not existed
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<String> getStringDetail(String flagKeyName, String defaultValue);

    /**
     * return the Boolean value of a given flag key name, default value if flag not existed
     * or this value is not a Boolean
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Boolean value, default value if flag not existed or this value is not a Boolean
     */
    Boolean getBoolean(String flagKeyName, Boolean defaultValue);

    /**
     * returns an object that describes the way the Boolean flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Boolean
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Boolean> getBooleanDetail(String flagKeyName, Boolean defaultValue);

    /**
     * return the Integer value of a given flag key name, default value if flag not existed
     * or this value is not an Integer
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Integer value, default value if flag not existed or this value is not an Integer
     */
    Integer getInteger(String flagKeyName, Integer defaultValue);

    /**
     * returns an object that describes the way the Integer flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not an Integer
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Integer> getIntegerDetail(String flagKeyName, Integer defaultValue);

    /**
     * return the Long value of a given flag key name, default value if flag not existed
     * or this value is not a Long
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Long value, default value if flag not existed or this value is not a Long
     */
    Long getLong(String flagKeyName, Long defaultValue);

    /**
     * returns an object that describes the way the Long flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Long
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Long> getLongDetail(String flagKeyName, Long defaultValue);

    /**
     * return the Double value of a given flag key name, default value if flag not existed
     * or this value is not a Double
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Double value, default value if flag not existed or this value is not a Double
     */
    Double getDouble(String flagKeyName, Double defaultValue);

    /**
     * returns an object that describes the way the Double flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Double
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Double> getDoubleDetail(String flagKeyName, Double defaultValue);

    /**
     * return the T type value of a given flag key name, default value if flag not existed
     * or this value is not a T type
     *
     * @param <T>          the type of flag value
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Json value, default value if flag not existe or this value is not a T type
     */
    <T> T getJsonObject(String flagKeyName, T defaultValue, Class<T> clazz);


    /**
     * returns an object that describes the way the T type flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a T type
     *
     * @param <T>          the type of flag value
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    <T> EvalDetail<T> getJsonDetail(String flagKeyName, T defaultValue, Class<T> clazz);
}
