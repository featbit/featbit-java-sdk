package co.featbit.server;

import co.featbit.server.exterior.InsightEventSenderFactory;
import co.featbit.server.exterior.InsightProcessorFactory;
import co.featbit.server.exterior.InsightProcessor;

import java.time.Duration;

/**
 * Factory to create {@link InsightProcessor}
 * <p>
 * The SDK normally buffers analytics events and sends them to feature flag center at intervals. If you want
 * to customize this behavior, create a builder with {@link Factory#insightProcessorFactory()}, change its
 * properties with the methods of this class, and pass it to {@link FFCConfig.Builder#insightProcessorFactory(InsightProcessorFactory)}:
 * <pre><code>
 *      InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
 *                     .capacity(10000)
 *
 *
 *             FFCConfig config = new FFCConfig.Builder()
 *                     .insightProcessorFactory(insightProcessorBuilder)
 *                     .build();
 *
 *             FFCClient client = new FFCClientImp(envSecret, config);
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

    protected int capacity;
    protected long retryIntervalInMilliseconds;
    protected int maxRetryTimes;
    protected long flushInterval;

    public InsightProcessorBuilder capacity(int capacityOfInbox) {
        this.capacity = capacityOfInbox;
        return this;
    }

    public InsightProcessorBuilder flushInterval(int flushIntervalInSecond) {
        this.flushInterval = (flushIntervalInSecond < 0) ? DEFAULT_FLUSH_INTERVAL : Duration.ofSeconds(flushIntervalInSecond).toMillis();
        return this;
    }

    public InsightProcessorBuilder retryInterval(long retryIntervalInMilliseconds) {
        this.retryIntervalInMilliseconds = retryIntervalInMilliseconds;
        return this;
    }

    public InsightProcessorBuilder maxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
        return this;
    }


}
