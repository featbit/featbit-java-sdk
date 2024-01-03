package co.featbit.commons.model;

import co.featbit.commons.json.JsonHelper;
import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * An object combines the result of a flag evaluation with an explanation of how it was calculated.
 * This object contains the details of evaluation of feature flag.
 *
 * @param <T> - String/Boolean/Numeric Type
 */
public final class EvalDetail<T> implements Serializable {
    private final T variation;

    private final String variationIndex;

    private final String reason;

    private final String name;

    private final String keyName;

    private EvalDetail(T variation,
                       String variationIndex,
                       String reason,
                       String keyName,
                       String name) {
        this.variation = variation;
        this.variationIndex = variationIndex;
        this.reason = reason;
        this.keyName = keyName;
        this.name = name;
    }

    /**
     * build method, this method is only for internal use
     *
     * @param variation      the result of flag value
     * @param variationIndex the index of variation
     * @param reason         main factor that influenced the flag evaluation value
     * @param keyName        key name of the flag
     * @param name           name of the flag
     * @param <T>            String/Boolean/Numeric Type
     * @return an EvalDetail
     */
    public static <T> EvalDetail<T> of(T variation,
                                       String variationIndex,
                                       String reason,
                                       String keyName,
                                       String name) {
        return new EvalDetail<>(variation, variationIndex, reason, keyName, name);
    }

    /**
     * build an instance from anthor EvalDetail
     *
     * @param variation the result of flag value
     * @param another   another EvalDetail
     * @param <T>
     * @param <S>
     * @return an EvalDetail
     */
    public static <T, S> EvalDetail<T> of(T variation, EvalDetail<S> another) {
        return new EvalDetail<>(variation, another.variationIndex, another.reason, another.keyName, another.name);
    }

    public boolean isDefaultVariation() {
        return variationIndex.equals("-1");
    }

    /**
     * build the method from a json string, this method is only for internal use
     *
     * @param json json string of an EvalDetail
     * @param cls  raw type of flag value
     * @param <T>  String/Boolean/Numeric Type
     * @return an EvalDetail
     */
    public static <T> EvalDetail<T> fromJson(String json, Class<T> cls) {
        Type type = new TypeToken<EvalDetail<T>>() {
        }.getType();
        return JsonHelper.deserialize(json, type);
    }

    /**
     * return a feature flag evaluation value
     *
     * @return the flag value
     */
    public T getVariation() {
        return variation;
    }

    /**
     * get the reason that evaluate the flag value.
     *
     * @return a string
     */
    public String getReason() {
        return reason;
    }

    /**
     * name of the flag associated
     *
     * @return a string
     */
    public String getName() {
        return name;
    }

    /**
     * key name of the flag associated
     *
     * @return a string
     */
    public String getKeyName() {
        return keyName;
    }

    /**
     * object converted to json string
     *
     * @return a json string
     */
    public String jsonfy() {
        return JsonHelper.serialize(this);
    }

    /**
     * get the index of variation
     *
     * @return a string
     */
    public String getVariationIndex() {
        return variationIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalDetail<?> that = (EvalDetail<?>) o;
        return Objects.equals(variation, that.variation) && Objects.equals(variationIndex, that.variationIndex) && Objects.equals(reason, that.reason) && Objects.equals(name, that.name) && Objects.equals(keyName, that.keyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variation, variationIndex, reason, name, keyName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("variation", variation)
                .add("variationIndex", variationIndex)
                .add("reason", reason)
                .add("name", name)
                .add("keyName", keyName)
                .toString();
    }
}
