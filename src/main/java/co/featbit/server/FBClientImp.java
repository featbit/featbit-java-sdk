package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.*;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static co.featbit.server.Evaluator.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A client for the Featbit API. The client is thread-safe.
 */
public final class FBClientImp implements FBClient {

    private final static Logger logger = Loggers.CLIENT;
    private final boolean offline;
    private final DataStorage storage;
    private final Evaluator evaluator;
    private final DataSynchronizer dataSynchronizer;
    private final Status.DataUpdateStatusProvider dataUpdateStatusProvider;
    private final Status.DataUpdater dataUpdater;
    private final InsightProcessor insightProcessor;
    private final ThreadPoolExecutor sharedExecutorService;
    private final Consumer<InsightTypes.Event> eventHandler;
    private final FlagTracker flagTracker;

    /**
     * Creates a new client to connect to feature flag center with a specified configuration.
     * <p>
     * This constructor can be used to configure advanced SDK features; see {@link FBConfig.Builder}.
     * <p>
     * Applications SHOULD instantiate a single instance for the lifetime of the application. In
     * the case where an application needs to evaluate feature flags from different environments,
     * you may create multiple clients, but they should still be retained
     * for the lifetime of the application rather than created per request or per thread.
     * <p>
     * Note that unless client is configured in offline mode{@link FBConfig.Builder#offline(boolean)} or set by
     * {@link Factory#externalDataSynchronization()}, this client try to connect to feature flag center
     * as soon as the constructor is called. The constructor will return when it successfully
     * connects, or when the timeout set by {@link FBConfig.Builder#startWaitTime(Duration)} (default:
     * 15 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout
     * elapses, you will receive the client in an uninitialized state where feature flags will return
     * default values; it will still continue trying to connect in the background unless there has been an {@link java.net.ProtocolException}
     * or you close the client{@link #close()}. You can detect whether initialization has succeeded by calling {@link #isInitialized()}.
     * <p>
     * If you prefer to have the constructor return immediately, and then wait for initialization to finish
     * at some other point, you can use {@link #getDataUpdateStatusProvider()} as follows:
     * <pre><code>
     *     FBConfig config = new FBConfig.Builder()
     *         .startWait(Duration.ZERO)
     *         .streamingURI("your streaming URI")
     *         .eventURI("your event URI")
     *         .build();
     *     FBClient client = new FBClientImp(sdkKey, config);
     *
     *     // later, when you want to wait for initialization to finish:
     *     boolean inited = client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(15))
     *     if (!inited) {
     *         // do whatever is appropriate if initialization has timed out
     *     }
     * </code></pre>
     * <p>
     * This constructor can throw unchecked exceptions if it is immediately apparent that
     * the SDK cannot work with these parameters. In fact, if the env secret is not valid,
     * it will throw an {@link IllegalArgumentException}  a null value for a non-nullable
     * parameter may throw a {@link NullPointerException}. The constructor will not throw
     * any exception that could only be detected after making a request to our API
     *
     * @param envSecret the secret key for your own environment
     * @param config    a client configuration object {@link FBConfig}
     * @throws NullPointerException     if a non-nullable parameter was null
     * @throws IllegalArgumentException if envSecret is invalid
     */
    public FBClientImp(String envSecret, FBConfig config) {
        checkNotNull(config, "FBConfig Should not be null");
        this.offline = config.isOffline();
        if (!this.offline) {
            checkArgument(Utils.isValidEnvSecret(envSecret), "envSecret is invalid");
            checkArgument(Utils.isUrl(config.getStreamingURL()) || Utils.isUrl(config.getEventURL()), "streaming or event url is invalid");
        }
        ContextImp context = new ContextImp(envSecret, config);
        //init components
        //Insight processor
        this.insightProcessor = config.getInsightProcessorFactory().createInsightProcessor(context);
        this.eventHandler = this.insightProcessor::send;
        //data storage
        this.storage = config.getDataStorageFactory().createDataStorage(context);
        //evaluator
        Evaluator.Getter<DataModel.FeatureFlag> flagGetter = key -> {
            DataStorageTypes.Item item = this.storage.get(DataStorageTypes.FEATURES, key);
            return item == null ? null : (DataModel.FeatureFlag) item;
        };
        Evaluator.Getter<DataModel.Segment> segmentGetter = key -> {
            DataStorageTypes.Item item = this.storage.get(DataStorageTypes.SEGMENTS, key);
            return item == null ? null : (DataModel.Segment) item;
        };
        this.evaluator = new EvaluatorImp(flagGetter, segmentGetter);

        this.sharedExecutorService = new ScheduledThreadPoolExecutor(1, Utils.createThreadFactory("featbit-shared-worker-%d", true));
        EventBroadcasterImpl<Status.StateListener, Status.State> dataUpdateStateNotifier = EventBroadcasterImpl.forDataUpdateStates(this.sharedExecutorService, logger);
        EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier = EventBroadcasterImpl.forFlagChangeEvents(this.sharedExecutorService, logger);
        this.flagTracker = new FlagTrackerImpl(flagChangeEventNotifier, (key, user) -> variation(key, user, null));
        //data updator
        Status.DataUpdaterImpl dataUpdatorImpl = new Status.DataUpdaterImpl(this.storage, dataUpdateStateNotifier, flagChangeEventNotifier);
        this.dataUpdater = dataUpdatorImpl;
        //data processor
        this.dataSynchronizer = config.getDataSynchronizerFactory().createDataSynchronizer(context, dataUpdatorImpl);
        //data update status provider
        this.dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdatorImpl, dataUpdateStateNotifier);

        // data sync
        Duration startWait = config.getStartWaitTime();
        Future<Boolean> initFuture = this.dataSynchronizer.start();
        if (!startWait.isZero() && !startWait.isNegative()) {
            try {
                if (!(config.getDataSynchronizerFactory() instanceof FactoryImp.NullDataSynchronizerFactory)) {
                    logger.info("FB JAVA SDK: waiting for Client initialization in {} milliseconds", startWait.toMillis());
                }
                if (config.getDataStorageFactory() instanceof FactoryImp.NullDataStorageFactory
                        || (!dataUpdater.storageInitialized() && !offline)) {
                    logger.info("SDK just returns default variation because of no data found in the given environment");
                }
                boolean initResult = initFuture.get(startWait.toMillis(), TimeUnit.MILLISECONDS);
                if (!initResult) {
                    logger.warn("FB JAVA SDK: SDK was not successfully initialized");
                }
            } catch (TimeoutException e) {
                logger.error("FB JAVA SDK: timeout encountered when waiting for data update");
            } catch (Exception e) {
                logger.error("FB JAVA SDK: exception encountered when waiting for data update", e);
            }
        } else {
            logger.info("FB JAVA SDK: SDK starts in asynchronous mode");
        }
    }

    @Override
    public boolean isInitialized() {
        return dataSynchronizer.isInitialized();
    }

    @Override
    public String variation(String featureFlagKey, FBUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, null);
        return res.getValue();
    }

    @Override
    public EvalDetail<String> variationDetail(String featureFlagKey, FBUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, null);
        return res.toEvalDetail(res.getValue());
    }

    @Override
    public boolean boolVariation(String featureFlagKey, FBUser user, Boolean defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Boolean.class);
        return BooleanUtils.toBoolean(res.getValue());
    }

    @Override
    public EvalDetail<Boolean> boolVariationDetail(String featureFlagKey, FBUser user, Boolean defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Boolean.class);
        return res.toEvalDetail(BooleanUtils.toBoolean(res.getValue()));
    }

    public double doubleVariation(String featureFlagKey, FBUser user, Double defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Double.class);
        return Double.parseDouble(res.getValue());
    }


    @Override
    public EvalDetail<Double> doubleVariationDetail(String featureFlagKey, FBUser user, Double defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Double.class);
        return res.toEvalDetail(Double.parseDouble(res.getValue()));
    }

    public int intVariation(String featureFlagKey, FBUser user, Integer defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Integer.class);
        return Double.valueOf(res.getValue()).intValue();
    }

    @Override
    public EvalDetail<Integer> intVariationDetail(String featureFlagKey, FBUser user, Integer defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Integer.class);
        return res.toEvalDetail(Double.valueOf(res.getValue()).intValue());
    }

    public long longVariation(String featureFlagKey, FBUser user, Long defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Long.class);
        return Double.valueOf(res.getValue()).longValue();
    }

    @Override
    public EvalDetail<Long> longVariationDetail(String featureFlagKey, FBUser user, Long defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Long.class);
        return res.toEvalDetail(Double.valueOf(res.getValue()).longValue());
    }

    @Override
    public <T> T jsonVariation(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue) {
        String json = variation(featureFlagKey, user, DEFAULT_JSON_VALUE);
        return Utils.parseJsonObject(json, defaultValue, clazz, DEFAULT_JSON_VALUE.equals(json));
    }

    @Override
    public <T> EvalDetail<T> jsonVariationDetail(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, DEFAULT_JSON_VALUE, null);
        T value = Utils.parseJsonObject(res.getValue(), defaultValue, clazz, DEFAULT_JSON_VALUE.equals(res.getValue()));
        return res.toEvalDetail(value);
    }

    private Evaluator.EvalResult evaluateInternal(String featureFlagKey, FBUser user, Object defaultValue, Class<?> requiredType) {
        String dv = defaultValue == null ? null : defaultValue.toString();
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FB JAVA SDK: evaluation is called before Java SDK client is initialized for feature flag, well using the default value");
                return Evaluator.EvalResult.error(dv, REASON_CLIENT_NOT_READY, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (StringUtils.isBlank(featureFlagKey)) {
                Loggers.EVALUATION.warn("FB JAVA SDK: null feature flag key; returning default value");
                return Evaluator.EvalResult.error(dv, REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            DataModel.FeatureFlag flag = getFlagInternal(featureFlagKey);
            if (flag == null) {
                Loggers.EVALUATION.warn("FB JAVA SDK: unknown feature flag {}; returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(dv, REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FB JAVA SDK: null user for feature flag {}, returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(dv, REASON_USER_NOT_SPECIFIED, featureFlagKey, FLAG_NAME_UNKNOWN);
            }

            InsightTypes.Event event = InsightTypes.FlagEvent.of(user);
            Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
            if (requiredType != null && !Utils.checkType(flag.getVariationType(), requiredType, res.getValue())) {
                Loggers.EVALUATION.warn("FB JAVA SDK: evaluation result {} didn't matched expected type {}", res.getValue(), requiredType);
                return Evaluator.EvalResult.error(dv, REASON_WRONG_TYPE, res.getKeyName(), res.getName());
            }
            eventHandler.accept(event);
            return res;
        } catch (Exception ex) {
            logger.error("FB JAVA SDK: unexpected error in evaluation", ex);
            return Evaluator.EvalResult.error(dv, REASON_ERROR, featureFlagKey, FLAG_NAME_UNKNOWN);
        }

    }

    private DataModel.FeatureFlag getFlagInternal(String featureFlagKey) {
        DataStorageTypes.Item item = storage.get(DataStorageTypes.FEATURES, featureFlagKey);
        return item == null ? null : (DataModel.FeatureFlag) item;
    }

    public boolean isFlagKnown(String featureKey) {
        try {
            if (!isInitialized()) {
                logger.warn("FB JAVA SDK: isFlagKnown is called before Java SDK client is initialized for feature flag");
                return false;
            }
            return getFlagInternal(featureKey) != null;
        } catch (Exception ex) {
            logger.error("FB JAVA SDK: unexpected error in isFlagKnown", ex);
        }
        return false;

    }


    public void close() throws IOException {
        logger.info("FB JAVA SDK: Java SDK client is closing...");
        this.storage.close();
        this.dataSynchronizer.close();
        this.insightProcessor.close();
        Utils.shutDownThreadPool("featbit-shared-worker", this.sharedExecutorService, Duration.ofSeconds(2));
    }

    @Override
    public FlagTracker getFlagTracker() {
        return this.flagTracker;
    }

    @Override
    public Status.DataUpdateStatusProvider getDataUpdateStatusProvider() {
        return dataUpdateStatusProvider;
    }

    @Override
    public boolean initializeFromExternalJson(String json) {
        if (offline && StringUtils.isNotBlank(json)) {
            DataModel.All all = JsonHelper.deserialize(json, DataModel.All.class);
            if (all.isProcessData()) {
                DataModel.Data allData = all.data();
                Long version = allData.getTimestamp();
                Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allDataInStorageType = allData.toStorageType();
                return dataUpdater.init(allDataInStorageType, version);
            }
        }
        return false;
    }

    @Override
    public AllFlagStates getAllLatestFlagsVariations(FBUser user) {
        ImmutableMap.Builder<Evaluator.EvalResult, InsightTypes.FlagEvent> builder = ImmutableMap.builder();
        boolean success = true;
        String errorString = "";
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FB JAVA SDK: Evaluation is called before Java SDK client is initialized for feature flag");
                success = false;
                errorString = REASON_CLIENT_NOT_READY;
            } else if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FB JAVA SDK: null user or feature flag");
                success = false;
                errorString = REASON_USER_NOT_SPECIFIED;
            } else {
                Map<String, DataStorageTypes.Item> allFlags = this.storage.getAll(DataStorageTypes.FEATURES);
                for (DataStorageTypes.Item item : allFlags.values()) {
                    InsightTypes.FlagEvent event = InsightTypes.FlagEvent.of(user);
                    DataModel.FeatureFlag flag = (DataModel.FeatureFlag) item;
                    Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
                    builder.put(res, event);
                }
            }
        } catch (Exception ex) {
            logger.error("FB JAVA SDK: unexpected error in evaluation", ex);
            success = false;
            errorString = REASON_ERROR;
        }
        return new Implicits.ComplexAllFlagStates(success, errorString, builder.build(), eventHandler);
    }

    @Override
    public void flush() {
        this.insightProcessor.flush();
    }

    @Override
    public void identify(FBUser user) {
        if (user == null) {
            Loggers.CLIENT.warn("FB JAVA SDK: user invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.UserEvent.of(user);
        insightProcessor.send(event);
    }

    @Override
    public void trackMetric(FBUser user, String eventName) {
        trackMetric(user, eventName, 1.0);
    }

    @Override
    public void trackMetric(FBUser user, String eventName, double metricValue) {
        if (user == null || StringUtils.isBlank(eventName)) {
            Loggers.CLIENT.warn("FB JAVA SDK: event/user/metric invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user).add(InsightTypes.Metric.of(eventName, metricValue));
        insightProcessor.send(event);
    }

    @Override
    public void trackMetrics(FBUser user, String... eventNames) {
        if (user == null || eventNames == null || eventNames.length == 0) {
            Loggers.CLIENT.warn("FB JAVA SDK: user/events invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user);
        for (String eventName : eventNames) {
            if (StringUtils.isNotBlank(eventName)) {
                event.add(InsightTypes.Metric.of(eventName, 1.0));
            }
        }
        insightProcessor.send(event);
    }

    @Override
    public void trackMetrics(FBUser user, Map<String, Double> metrics) {
        if (user == null || metrics == null || metrics.isEmpty()) {
            Loggers.CLIENT.warn("FB JAVA SDK: user/metrics invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user);
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String eventName = entry.getKey();
            Double metricValue = entry.getValue();
            if (StringUtils.isNotBlank(eventName) && metricValue != null) {
                event.add(InsightTypes.Metric.of(eventName, metricValue));
            }
        }
        insightProcessor.send(event);
    }

}
