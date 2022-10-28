package co.featbit.server.exterior;

import org.apache.commons.lang3.StringUtils;

/**
 * the basic configuration of SDK that will be used for all components
 */
public final class BasicConfig {

    private static final String DEFAULT_STREAMING_PATH = "/streaming";

    private static final String DEFAULT_EVENT_PATH = "/api/public/insight/track";

    private final String envSecret;
    private final boolean offline;

    private final String streamingURI;

    private final String eventURI;


    /**
     * constructs an instance
     *
     * @param envSecret the env secret of your env
     * @param offline   true if the SDK was configured to be completely offline
     */
    public BasicConfig(String envSecret,
                       boolean offline,
                       String streamingURL,
                       String eventURL) {
        this.envSecret = envSecret;
        this.offline = offline;
        this.streamingURI = StringUtils.stripEnd(streamingURL, "/").concat(DEFAULT_STREAMING_PATH);
        this.eventURI = StringUtils.stripEnd(eventURL, "/").concat(DEFAULT_EVENT_PATH);
    }

    /**
     * return the env secret
     *
     * @return a string
     */
    public String getEnvSecret() {
        return envSecret;
    }

    /**
     * Returns true if the client was configured to be completely offline.
     *
     * @return true if offline
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * return the default streaming url
     *
     * @return a string
     */
    public String getStreamingURI() {
        return streamingURI;
    }

    /**
     * return the default event url
     *
     * @return a string
     */
    public String getEventURI() {
        return eventURI;
    }
}
