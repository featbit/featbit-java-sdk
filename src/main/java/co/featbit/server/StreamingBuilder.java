package co.featbit.server;

import co.featbit.server.exterior.DataSynchronizerFactory;

import java.time.Duration;

/**
 * Factory to create a {@link Streaming} implementation
 * By default, the SDK uses a streaming connection to receive feature flag data. If you want to customize the behavior of the connection,
 * create a builder with {@link Factory#dataSynchronizerFactory()}, change its properties with the methods of this class,
 * and pass it to {@link FBConfig.Builder#dataSynchronizerFactory(DataSynchronizerFactory)}:
 * <pre><code>
 *      StreamingBuilder streamingBuilder = Factory.streamingBuilder()
 *           .firstRetryDelay(Duration.ofSeconds(1));
 *       FBConfig config = new FBConfig.Builder()
 *           .updateProcessorFactory(streamingBuilder)
 *           .build();
 *       FBClient client = new FBClientImp(envSecret, config);
 * </code></pre>
 * <p>
 * Note that this class is in fact only internal use, it's not recommended to customize any behavior in this configuration.
 * We just keep the same design pattern in the SDK
 */
public abstract class StreamingBuilder implements DataSynchronizerFactory {
    protected static final Duration DEFAULT_FIRST_RETRY_DURATION = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DURATION = Duration.ofSeconds(60);
    protected Duration firstRetryDelay = DEFAULT_FIRST_RETRY_DURATION;
    protected Integer maxRetryTimes = 0;

    /**
     * Sets the initial reconnect delay for the streaming connection.
     * <p>
     * The streaming service uses a backoff algorithm (with jitter) every time the connection needs
     * to be reestablished. The delay for the first reconnection will start near this value, and then
     * increase exponentially for any subsequent connection failures.
     *
     * @param duration the reconnecting time base value; null to use the default(1s)
     * @return the builder
     */
    public StreamingBuilder firstRetryDelay(Duration duration) {
        this.firstRetryDelay = (duration == null || duration.minusSeconds(1).isNegative() || MAX_RETRY_DURATION.minus(duration).isNegative())
                        ? DEFAULT_FIRST_RETRY_DURATION : duration;
        return this;
    }

    /**
     * Sets the max retry times for the streaming failures.
     *
     * @param maxRetryTimes an int value if less than or equals to 0, use the default
     * @return the builder
     */
    public StreamingBuilder maxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = (maxRetryTimes <= 0) ? Integer.MAX_VALUE : maxRetryTimes;
        return this;
    }
}
