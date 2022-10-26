package co.featbit.server;

import co.featbit.server.exterior.BasicConfig;
import co.featbit.server.exterior.Context;
import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageFactory;
import co.featbit.server.exterior.DataStoreTypes;
import co.featbit.server.exterior.DataSynchronizer;
import co.featbit.server.exterior.DataSynchronizerFactory;
import co.featbit.server.exterior.DefaultSender;
import co.featbit.server.exterior.HttpConfig;
import co.featbit.server.exterior.HttpConfigurationBuilder;
import co.featbit.server.exterior.InsightProcessor;
import co.featbit.server.exterior.InsightProcessorFactory;
import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

abstract class FactoryImp {
    static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
        @Override
        public HttpConfig createHttpConfig(BasicConfig config) {
            connectTime = connectTime == null ? DEFAULT_CONN_TIME : connectTime;
            socketTime = socketTime == null ? DEFAULT_SOCK_TIME : socketTime;
            return new HttpConfigImpl(connectTime,
                    socketTime,
                    proxy,
                    authenticator,
                    socketFactory,
                    sslSocketFactory,
                    x509TrustManager,
                    Utils.defaultHeaders(config.getEnvSecret()));
        }
    }

    static final class StreamingBuilderImpl extends StreamingBuilder {
        @Override
        public DataSynchronizer createDataSynchronizer(Context config, Status.DataUpdater dataUpdater) {
            Loggers.UPDATE_PROCESSOR.debug("Choose Streaming Update Processor");
            firstRetryDelay = firstRetryDelay == null ? DEFAULT_FIRST_RETRY_DURATION : firstRetryDelay;
            return new Streaming(dataUpdater, config, firstRetryDelay, maxRetryTimes);
        }
    }

    static final class InMemoryDataStorageFactory implements DataStorageFactory {
        static final InMemoryDataStorageFactory SINGLETON = new InMemoryDataStorageFactory();

        @Override
        public DataStorage createDataStorage(Context config) {
            return new InMemoryDataStorage();
        }
    }

    static class NullDataStorageFactory implements DataStorageFactory {

        static final NullDataStorageFactory SINGLETON = new NullDataStorageFactory();

        @Override
        public DataStorage createDataStorage(Context config) {
            Loggers.CLIENT.debug("Null Data Storage is only used for test");
            return NullDataStorage.SINGLETON;
        }
    }

    private static final class NullDataStorage implements DataStorage {

        static final NullDataStorage SINGLETON = new NullDataStorage();

        @Override
        public void init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version) {

        }

        @Override
        public DataStoreTypes.Item get(DataStoreTypes.Category category, String key) {
            return null;
        }

        @Override
        public Map<String, DataStoreTypes.Item> getAll(DataStoreTypes.Category category) {
            return ImmutableMap.of();
        }

        @Override
        public boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version) {
            return true;
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public long getVersion() {
            return 0;
        }

        @Override
        public void close() {
        }
    }

    static final class NullDataSynchronizerFactory implements DataSynchronizerFactory {

        static final NullDataSynchronizerFactory SINGLETON = new NullDataSynchronizerFactory();

        @Override
        public DataSynchronizer createDataSynchronizer(Context config, Status.DataUpdater dataUpdater) {
            if (config.basicConfig().isOffline()) {
                Loggers.CLIENT.debug("SDK is in offline mode");
            } else {
                Loggers.CLIENT.debug("SDK won't connect to feature flag center");
            }
            return new NullDataSynchronizer(dataUpdater);
        }
    }

    private static final class NullDataSynchronizer implements DataSynchronizer {

        private final Status.DataUpdater dataUpdater;

        NullDataSynchronizer(Status.DataUpdater dataUpdater) {
            this.dataUpdater = dataUpdater;
        }

        @Override
        public Future<Boolean> start() {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public boolean isInitialized() {
            return dataUpdater.storageInitialized();
        }

        @Override
        public void close() {

        }
    }

    static final class InsightProcessBuilderImpl extends InsightProcessorBuilder {
        @Override
        public DefaultSender createInsightEventSender(Context context) {
            maxRetryTimes = maxRetryTimes < 0 ? DEFAULT_RETRY_TIMES : maxRetryTimes;
            retryIntervalInMilliseconds = retryIntervalInMilliseconds <= 0 ? DEFAULT_RETRY_DELAY : retryIntervalInMilliseconds;
            return new Senders.InsightEventSenderImp(context.http(), maxRetryTimes, Duration.ofMillis(retryIntervalInMilliseconds));
        }

        @Override
        public InsightProcessor createInsightProcessor(Context context) {
            DefaultSender sender = createInsightEventSender(context);
            return new Insights.InsightProcessorImpl(context.basicConfig().getEventURI(),
                    sender,
                    Math.max(DEFAULT_FLUSH_INTERVAL, flushInterval),
                    Math.max(DEFAULT_CAPACITY, capacity));
        }
    }

    static final class NullInsightProcessorFactory implements InsightProcessorFactory {
        static final NullInsightProcessorFactory SINGLETON = new NullInsightProcessorFactory();

        @Override
        public InsightProcessor createInsightProcessor(Context context) {
            Loggers.CLIENT.debug("Null Insight processor is only used in offline mode");
            return NullInsightProcessor.SINGLETON;
        }
    }

    static final class NullInsightProcessor implements InsightProcessor {

        static final NullInsightProcessor SINGLETON = new NullInsightProcessor();


        @Override
        public void send(InsightTypes.Event event) {

        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }
    }


}
