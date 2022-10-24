package co.featbit.server.exterior;

/**
 * Interface for a factory that creates an {@link HttpConfig}.
 */
public interface HttpConfigFactory {
    /**
     * Creates the http configuration.
     *
     * @param config provides the basic SDK configuration properties
     * @return an {@link HttpConfig} instance
     */
    HttpConfig createHttpConfig(BasicConfig config);
}
