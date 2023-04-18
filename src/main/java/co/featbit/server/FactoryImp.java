package co.featbit.server;

import co.featbit.server.exterior.BasicConfig;
import co.featbit.server.exterior.Context;
import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageFactory;
import co.featbit.server.exterior.DataStorageTypes;
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
        public boolean init(Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData, Long version) {
            return true;
        }

        @Override
        public DataStorageTypes.Item get(DataStorageTypes.Category category, String key) {
            return null;
        }

        @Override
        public Map<String, DataStorageTypes.Item> getAll(DataStorageTypes.Category category) {
            return ImmutableMap.of();
        }

        @Override
        public boolean upsert(DataStorageTypes.Category category, String key, DataStorageTypes.Item item, Long version) {
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
            dataUpdater.updateStatus(Status.State.OKState());
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void close() {

        }
    }

    static final class InsightProcessBuilderImpl extends InsightProcessorBuilder {
        @Override
        public DefaultSender createInsightEventSender(Context context) {
            return new Senders.InsightEventSenderImp(context.http(),
                    Math.min(maxRetryTimes, 3),
                    Duration.ofMillis(Math.min(retryIntervalInMilliseconds, Duration.ofSeconds(1).toMillis())));
        }

        @Override
        public InsightProcessor createInsightProcessor(Context context) {
            DefaultSender sender = createInsightEventSender(context);
            return new Insights.InsightProcessorImpl(context.basicConfig().getEventURI(),
                    sender,
                    Math.min(flushIntervalInMilliseconds, Duration.ofSeconds(3).toMillis()),
                    Math.min(capacity, DEFAULT_CAPACITY));
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
