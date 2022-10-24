package co.featbit.commons.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A collection of attributes that can affect flag evaluation, usually corresponding to a user of your application.
 * The mandatory properties are the key and name. The key must uniquely identify each user in an environment;
 * this could be a username or email address for authenticated users, or an ID for anonymous users.
 * The name is used to search your user quickly in feature flag center.
 * The custom properties are optional, you may also define custom properties with arbitrary names and values.
 */

public final class FFCUser implements Serializable {

    private final static Function<FFCUser, String> USERNAME = u -> u.userName;
    private final static Function<FFCUser, String> KEY = u -> u.key;
    private final static Map<String, Function<FFCUser, String>> BUILTINS = ImmutableMap.of("name", USERNAME, "keyid", KEY);
    private final String userName;
    private final String key;
    private final Map<String, String> custom;


    private FFCUser(Builder builder) {
        String key = builder.key;
        String userName = builder.userName;
        checkArgument(StringUtils.isNotBlank(key), "Key shouldn't be empty");
        checkArgument(StringUtils.isNotBlank(userName), "UserName shouldn't be empty");
        this.key = key;
        this.userName = userName;
        ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
        for (Map.Entry<String, String> entry : builder.custom.entrySet()) {
            if (!BUILTINS.containsKey(entry.getKey().toLowerCase())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        this.custom = map.build();
    }

    /**
     * returns user's name if presence
     *
     * @return a string or null
     */
    public String getUserName() {
        return userName;
    }

    /**
     * returns user's unique key.
     *
     * @return a string
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns a map of all custom attributes set for this user
     *
     * @return a map, note that this map is readonly
     */
    public Map<String, String> getCustom() {
        return custom;
    }

    /**
     * Gets the value of a user attribute, if present.
     * This can be either a built-in attribute or a custom one
     *
     * @param attribute – the attribute to get
     * @return the attribute value or null
     */
    public String getProperty(String attribute) {
        Function<FFCUser, String> f = BUILTINS.get(attribute.toLowerCase());
        if (f == null) {
            return custom.get(attribute);
        }
        return f.apply(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("userName", userName)
                .add("key", key)
                .add("custom", custom)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FFCUser ffcUser = (FFCUser) o;
        return Objects.equals(userName, ffcUser.userName) && Objects.equals(key, ffcUser.key) && Objects.equals(custom, ffcUser.custom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, key, custom);
    }

    /**
     * A builder  that helps construct FFCClient objects. Builder calls can be chained, supporting the following pattern:
     * <pre><code>
     *     FFCClient user = new FFCClient.Builder("key")
     *               .userName("name")
     *               .custom("property", "value")
     *               .build()
     * </code></pre>
     */

    public static class Builder {
        private String userName;

        private String key;

        private final Map<String, String> custom = new HashMap<>();

        /**
         * Creates a builder with the specified key
         *
         * @param key key
         */
        public Builder(String key) {
            this.key = key;
        }

        /**
         * Changes the user's key.
         *
         * @param s key
         * @return the builder
         */
        public Builder key(String s) {
            this.key = s;
            return this;
        }

        /**
         * set the user's userName.
         *
         * @param s username
         * @return the builder
         */
        public Builder userName(String s) {
            this.userName = s;
            return this;
        }

        /**
         * Adds a String-valued custom attribute. When set to one of the built-in user attribute keys
         * the key/value pair will be ignored.
         *
         * @param key   custom attribute name
         * @param value custom attribute value
         * @return the builder
         */
        public Builder custom(String key, String value) {
            if (StringUtils.isNotBlank(key) && value != null) {
                custom.put(key, value);
            }
            return this;
        }

        /**
         * Builds the configured FFCUser object.
         * Returns the FFCUser configured by this builder
         */
        public FFCUser build() {
            return new FFCUser(this);
        }
    }

}
