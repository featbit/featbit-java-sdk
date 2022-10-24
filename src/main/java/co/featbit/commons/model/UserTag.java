package co.featbit.commons.model;

import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * an object provides the user tags used to instantiate the FBUser
 */
public class UserTag implements Serializable {

    public static final String HEADER = "header";
    public static final String QUERY_STRING = "querystring";
    public static final String COOKIE = "cookie";
    public static final String POST_BODY = "body";

    private final String requestProperty;
    private final String source;
    private final String userProperty;

    /**
     * @param requestProperty tag name in http request
     * @param source          tag source: header, query string, cookie or post body
     * @param userProperty    tag name in {@link FBUser}
     */
    public UserTag(String requestProperty, String source, String userProperty) {
        this.requestProperty = requestProperty;
        this.source = source;
        this.userProperty = userProperty;
    }

    /**
     * build a user tag
     *
     * @param requestProperty tag name in http request
     * @param source          tag source: header, query string, cookie or post body
     * @param userProperty    tag name in {@link FBUser}
     * @return a User tag
     */
    public static UserTag of(String requestProperty, String source, String userProperty) {
        return new UserTag(requestProperty, source, userProperty);
    }

    /**
     * return the tag name in http request
     *
     * @return a string
     */
    public String getRequestProperty() {
        return requestProperty;
    }

    /**
     * return the tag source
     *
     * @return a string
     */
    public String getSource() {
        return source;
    }

    /**
     * return the property name of {@link FBUser}
     *
     * @return a string or null
     */
    public String getUserProperty() {
        return userProperty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserTag userTag = (UserTag) o;
        return Objects.equals(requestProperty, userTag.requestProperty) && Objects.equals(source, userTag.source) && Objects.equals(userProperty, userTag.userProperty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestProperty, source, userProperty);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("requestProperty", requestProperty)
                .add("source", source)
                .add("userProperty", userProperty)
                .toString();
    }
}
