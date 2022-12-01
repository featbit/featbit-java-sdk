package co.featbit.server;

import co.featbit.server.exterior.InsightEventSenderFactory;
import co.featbit.server.exterior.InsightProcessor;
import co.featbit.server.exterior.InsightProcessorFactory;

import java.time.Duration;

/**
 * Factory to create {@link InsightProcessor}
 * <p>
 * The SDK normally buffers analytics events and sends them to feature flag center at intervals. If you want
 * to customize this behavior, create a builder with {@link Factory#insightProcessorFactory()}, change its
 * properties with the methods of this class, and pass it to {@link FBConfig.Builder#insightProcessorFactory(InsightProcessorFactory)}:
 * <pre><code>
 *      InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
 *                     .capacity(10000)
 *
 *
 *             FBConfig config = new FBConfig.Builder()
 *                     .insightProcessorFactory(insightProcessorBuilder)
 *                     .build();
 *
 *             FBClient client = new FBClientImp(envSecret, config);
 * </code></pre>
 * <p>
 * Note that this class is in fact only internal use, it's not recommended to customize any behavior in this configuration.
 * We just keep the same design pattern in the SDK
 */

public abstract class InsightProcessorBuilder implements InsightEventSenderFactory, InsightProcessorFactory {
    protected final static int DEFAULT_CAPACITY = 10000;
    protected final static int DEFAULT_RETRY_DELAY = 100;
    protected final static int DEFAULT_RETRY_TIMES = 1;
    protected final static long DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(1).toMillis();

    protected int capacity = DEFAULT_CAPACITY;
    protected long retryIntervalInMilliseconds = DEFAULT_RETRY_DELAY;
    protected int maxRetryTimes = DEFAULT_RETRY_TIMES;
    protected long flushIntervalInMilliseconds = DEFAULT_FLUSH_INTERVAL;

    /**
     * the capacity of message inbox which stores temporarily insight messages, default value is 10000
     *
     * @param capacityOfInbox
     * @return InsightProcessorBuilder
     */
    public InsightProcessorBuilder capacity(int capacityOfInbox) {
        this.capacity = (capacityOfInbox < 0) ? DEFAULT_CAPACITY : capacityOfInbox;
        return this;
    }

    /**
     * the interval to flush automatically insight messages, the default value is 1 seconds
     *
     * @param flushIntervalInMilliseconds
     * @return
     */
    public InsightProcessorBuilder flushInterval(long flushIntervalInMilliseconds) {
        this.flushIntervalInMilliseconds = (flushIntervalInMilliseconds < 0) ? DEFAULT_FLUSH_INTERVAL : flushIntervalInMilliseconds;
        return this;
    }

    /**
     * retry interval for sending failure, the default value is 0.1 seconds
     *
     * @param retryIntervalInMilliseconds
     * @return
     */
    public InsightProcessorBuilder retryInterval(long retryIntervalInMilliseconds) {
        this.retryIntervalInMilliseconds = (retryIntervalInMilliseconds < 0) ? DEFAULT_RETRY_DELAY : retryIntervalInMilliseconds;
        return this;
    }

    /**
     * max number of retries for sending failure, default value is 1 time
     *
     * @param maxRetryTimes
     * @return
     */
    public InsightProcessorBuilder maxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = (maxRetryTimes < 0) ? DEFAULT_RETRY_TIMES : maxRetryTimes;
        return this;
    }


}
