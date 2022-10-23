package co.featbit.commons.model;

import co.featbit.commons.json.JsonHelper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The object provides a standard return responding the request of getting all flag values from a client sdk
 *
 * @param <T> String/Boolean/Numeric Type
 */
public class AllFlagStates<T> extends BasicFlagState implements Serializable {
    private List<EvalDetail<T>> data;

    private transient Map<String, EvalDetail<T>> cache;


    protected AllFlagStates(boolean success, String message, List<EvalDetail<T>> data) {
        super(success, success ? "OK" : message);
        init(data);
    }

    private void init(List<EvalDetail<T>> data) {
        ImmutableMap.Builder<String, EvalDetail<T>> builder = ImmutableMap.builder();
        this.data = data == null ? ImmutableList.of() : ImmutableList.copyOf(data);
        for (EvalDetail<T> detail : data) {
            builder.put(detail.getKeyName(), detail);
        }
        this.cache = builder.build();
    }

    /**
     * build a AllFlagStates without flag value
     *
     * @param message the reason without flag value
     * @return a AllFlagStates
     */
    public static <T> AllFlagStates<T> empty(String message) {
        return new AllFlagStates(false, message, null);
    }

    /**
     * build a AllFlagStates
     *
     * @param success true if the last request is successful
     * @param message the reason
     * @param data    all flag values
     * @param <T>     String/Boolean/Numeric Type
     * @return a AllFlagStates
     */
    public static <T> AllFlagStates<T> of(boolean success, String message, List<EvalDetail<T>> data) {
        return new AllFlagStates(success, success ? "OK" : message, data);
    }

    /**
     * build a AllFlagStates from json
     *
     * @param json a string json
     * @param cls
     * @param <T>  String/Boolean/Numeric Type
     * @return a AllFlagStates
     */
    public static <T> AllFlagStates<T> fromJson(String json, Class<T> cls) {
        return JsonHelper.deserialize(json, new TypeToken<AllFlagStates<T>>() {
        }.getType());
    }

    /**
     * return details of all the flags
     *
     * @return a map of flag key name and the {@link Function} to get the its {@link EvalDetail}
     */
    public final Map<String, Function<String, EvalDetail<T>>> getData() {
        ImmutableMap.Builder<String, Function<String, EvalDetail<T>>> map = ImmutableMap.builder();
        for (EvalDetail<T> detail : data) {
            map.put(detail.getKeyName(), this::get);
        }
        return map.build();
    }

    /**
     * return a detail of a given flag key name
     *
     * @param flagKeyName flag key name
     * @return an {@link EvalDetail}
     */
    public EvalDetail<T> get(String flagKeyName) {
        if (cache == null || cache.isEmpty()) {
            init(data);
        }
        return cache.get(flagKeyName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("success", success)
                .add("message", message)
                .add("data", data)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AllFlagStates<?> that = (AllFlagStates<?>) o;
        return Objects.equals(data, that.data) && Objects.equals(message, that.message)
                && Objects.equals(success, that.success);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, data);
    }
}
