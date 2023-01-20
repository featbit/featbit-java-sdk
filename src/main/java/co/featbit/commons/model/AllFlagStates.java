package co.featbit.commons.model;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.json.JsonParseException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The object provides a standard return responding the request of getting all flag values from a client sdk
 */
public class AllFlagStates extends BasicFlagState implements Serializable {

    protected static final String DEFAULT_JSON_VALUE = "DJV";

    protected static final String FLAG_VALUE_PARSING_ERROR = "flag value parsing error";

    protected static final String FLAG_NAME_UNKNOWN = "flag name unknown";

    private List<EvalDetail<String>> data;

    protected transient Map<String, EvalDetail<String>> cache;


    protected AllFlagStates(boolean success, String message, List<EvalDetail<String>> data) {
        super(success, success ? "OK" : message);
        init(data);
    }

    private void init(List<EvalDetail<String>> data) {
        ImmutableMap.Builder<String, EvalDetail<String>> builder = ImmutableMap.builder();
        this.data = data == null ? ImmutableList.of() : ImmutableList.copyOf(data);
        for (EvalDetail<String> detail : data) {
            builder.put(detail.getKeyName(), detail);
        }
        this.cache = builder.build();
    }

    /**
     * build a AllFlagStates
     *
     * @param success true if the last request is successful
     * @param message the reason
     * @param data    all flag values
     * @return a AllFlagStates
     */
    public static AllFlagStates of(boolean success, String message, List<EvalDetail<String>> data) {
        return new AllFlagStates(success, success ? "OK" : message, data);
    }

    /**
     * build a AllFlagStates from json
     *
     * @param json a string json
     * @return a AllFlagStates
     */
    public static AllFlagStates fromJson(String json) {
        return JsonHelper.deserialize(json, new TypeToken<AllFlagStates>() {
        }.getType());
    }

    private <T> EvalDetail<T> getInternal(String flagKeyName, T defaultValue, Class<T> clzz) {
        if (cache == null || cache.isEmpty()) {
            init(data);
        }
        EvalDetail<String> ed = cache.get(flagKeyName);
        if (ed == null) return EvalDetail.of(defaultValue, FLAG_VALUE_PARSING_ERROR, flagKeyName, FLAG_NAME_UNKNOWN);
        Object res = null;
        String variation = ed.getVariation();
        if (clzz == String.class) {
            res = variation;
        } else if (clzz == Boolean.class) {
            res = BooleanUtils.toBooleanObject(variation);
        } else if (clzz == Integer.class || clzz == Double.class || clzz == Long.class) {
            if (StringUtils.isNumeric(variation)) {
                if (clzz == Integer.class) {
                    res = Integer.parseInt(variation);
                } else if (clzz == Double.class) {
                    res = Double.parseDouble(variation);
                } else {
                    res = Long.parseLong(variation);
                }
            }
        }
        return res == null ? EvalDetail.of(defaultValue, FLAG_VALUE_PARSING_ERROR, flagKeyName, ed.getName()) : EvalDetail.of(clzz.cast(res), ed.getReason(), flagKeyName, ed.getName());
    }

    /**
     * return the string value of a given flag key name or default value if flag not existed
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return string value or default value if flag not existed
     */
    public String getString(String flagKeyName, String defaultValue) {
        return getInternal(flagKeyName, defaultValue, String.class).getVariation();
    }

    /**
     * returns an object that describes the way the string flag value was determined.
     * Note that default value in the detail if flag not existed
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public EvalDetail<String> getStringDetail(String flagKeyName, String defaultValue) {
        return getInternal(flagKeyName, defaultValue, String.class);
    }

    /**
     * return the Boolean value of a given flag key name, default value if flag not existed
     * or this value is not a Boolean
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Boolean value, default value if flag not existed or this value is not a Boolean
     */
    public Boolean getBoolean(String flagKeyName, Boolean defaultValue) {
        return getInternal(flagKeyName, defaultValue, Boolean.class).getVariation();
    }

    /**
     * returns an object that describes the way the Boolean flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Boolean
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public EvalDetail<Boolean> getBooleanDetail(String flagKeyName, Boolean defaultValue) {
        return getInternal(flagKeyName, defaultValue, Boolean.class);
    }

    /**
     * return the Integer value of a given flag key name, default value if flag not existed
     * or this value is not an Integer
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Integer value, default value if flag not existed or this value is not an Integer
     */
    public Integer getInteger(String flagKeyName, Integer defaultValue) {
        return getInternal(flagKeyName, defaultValue, Integer.class).getVariation();
    }

    /**
     * returns an object that describes the way the Integer flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not an Integer
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public EvalDetail<Integer> getIntegerDetail(String flagKeyName, Integer defaultValue) {
        return getInternal(flagKeyName, defaultValue, Integer.class);
    }

    /**
     * return the Long value of a given flag key name, default value if flag not existed
     * or this value is not a Long
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Long value, default value if flag not existed or this value is not a Long
     */
    public Long getLong(String flagKeyName, Long defaultValue) {
        return getInternal(flagKeyName, defaultValue, Long.class).getVariation();
    }

    /**
     * returns an object that describes the way the Long flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Long
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public EvalDetail<Long> getLongDetail(String flagKeyName, Long defaultValue) {
        return getInternal(flagKeyName, defaultValue, Long.class);
    }

    /**
     * return the Double value of a given flag key name, default value if flag not existed
     * or this value is not an Double
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Double value, default value if flag not existe or this value is not an Double
     */
    public Double getDouble(String flagKeyName, Double defaultValue) {
        return getInternal(flagKeyName, defaultValue, Double.class).getVariation();
    }

    /**
     * returns an object that describes the way the Double flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a Double
     *
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public EvalDetail<Double> getDoubleDetail(String flagKeyName, Double defaultValue) {
        return getInternal(flagKeyName, defaultValue, Double.class);
    }

    /**
     * return the T type value of a given flag key name, default value if flag not existed
     * or this value is not a T type
     *
     * @param <T>          the type of flag value
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return the Json value, default value if flag not existe or this value is not a T type
     */
    public <T> T getJsonObject(String flagKeyName, T defaultValue, Class<T> clazz) {
        EvalDetail<String> ed = getInternal(flagKeyName, DEFAULT_JSON_VALUE, String.class);
        String json = ed.getVariation();
        if (DEFAULT_JSON_VALUE.equals(json)) {
            return defaultValue;
        }
        try {
            return JsonHelper.deserialize(json, clazz);
        } catch (JsonParseException e) {
            return defaultValue;
        }
    }

    /**
     * returns an object that describes the way the T type flag value was determined.
     * Note that default value in the detail if flag not existed or the value is not a T type
     *
     * @param <T>          the type of flag value
     * @param flagKeyName  the unique key for the feature flag
     * @param defaultValue the default value of the flag
     * @return an {@link EvalDetail} object
     */
    public <T> EvalDetail<T> getJsonDetail(String flagKeyName, T defaultValue, Class<T> clazz) {
        EvalDetail<String> ed = getInternal(flagKeyName, DEFAULT_JSON_VALUE, String.class);
        String json = ed.getVariation();
        if (DEFAULT_JSON_VALUE.equals(json)) {
            return EvalDetail.of(defaultValue, ed.getReason(), flagKeyName, ed.getName());
        }
        try {
            T jsonValue = JsonHelper.deserialize(json, clazz);
            return EvalDetail.of(jsonValue, ed.getReason(), flagKeyName, ed.getName());
        } catch (JsonParseException e) {
            return EvalDetail.of(defaultValue, ed.getReason(), flagKeyName, ed.getName());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("success", success).add("message", message).add("data", data).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllFlagStates that = (AllFlagStates) o;
        return Objects.equals(data, that.data) && Objects.equals(cache, that.cache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, data);
    }
}
