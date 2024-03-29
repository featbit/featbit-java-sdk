package co.featbit.server.exterior;

import java.io.Closeable;

/**
 * interface for the http connection to help FeatBit API send or receive the details of feature flags, user segments, events etc.
 */
public interface DefaultSender extends Closeable {
    /**
     * send the json objects to feature flag center in the post method
     *
     * @param url      the url to send json
     * @param jsonBody json string
     * @return a response of FeatBit API
     */
    String postJson(String url, String jsonBody);
}
