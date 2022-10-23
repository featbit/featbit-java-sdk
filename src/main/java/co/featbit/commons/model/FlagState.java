package co.featbit.commons.model;

import co.featbit.commons.json.JsonHelper;
import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;

import java.io.Serializable;
import java.util.Objects;

/**
 * The object provides a standard return responding the request of getting a flag value from a client sdk
 *
 * @param <T> String/Boolean/Numeric Type
 */
public final class FlagState<T> extends BasicFlagState implements Serializable {
    private final EvalDetail<T> data;

    private FlagState(boolean success, String message, EvalDetail<T> data) {
        super(success, message);
        this.data = data;
    }

    /**
     * build a good flag stat
     *
     * @param data a flag value with reason
     * @param <T>  String/Boolean/Numeric Type
     * @return a FlagState
     */
    public static <T> FlagState<T> of(EvalDetail<T> data) {
        return new FlagState(data.isSuccess(),
                data.isSuccess() ? "OK" : data.getReason(),
                data);
    }

    /**
     * build a flag state without flag value
     *
     * @param message message the reason without flag value
     * @param <T>     String/Boolean/Numeric Type
     * @return a FlagState
     */
    public static <T> FlagState<T> Empty(String message) {
        return new FlagState(false, message, null);
    }

    /**
     * build a flag state from json
     * @param json a string json
     * @param cls
     * @param <T> String/Boolean/Numeric Type
     * @return a FlagState
     */
    public static <T> FlagState<T> fromJson(String json, Class<T> cls){
        return JsonHelper.deserialize(json, new TypeToken<FlagState<T>>(){}.getType());
    }

    /**
     * return the flag value with all the details
     *
     * @return {@link EvalDetail}
     */
    public EvalDetail<T> getData() {
        return data;
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
        FlagState<?> flagState = (FlagState<?>) o;
        return Objects.equals(success, flagState.success) && Objects.equals(message, flagState.message) &&
                Objects.equals(data, flagState.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, data);
    }

}
