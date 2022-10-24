package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.json.JsonParseException;
import co.featbit.commons.model.AllFlagStates;
import co.featbit.commons.model.EvalDetail;
import co.featbit.commons.model.FBUser;
import co.featbit.commons.model.FlagState;
import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStoreTypes;
import co.featbit.server.exterior.DataSynchronizer;
import co.featbit.server.exterior.FBClient;
import co.featbit.server.exterior.InsightProcessor;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static co.featbit.server.Evaluator.DEFAULT_JSON_VALUE;
import static co.featbit.server.Evaluator.FLAG_KEY_UNKNOWN;
import static co.featbit.server.Evaluator.FLAG_NAME_UNKNOWN;
import static co.featbit.server.Evaluator.FLAG_VALUE_UNKNOWN;
import static co.featbit.server.Evaluator.NO_EVAL_RES;
import static co.featbit.server.Evaluator.REASON_CLIENT_NOT_READY;
import static co.featbit.server.Evaluator.REASON_ERROR;
import static co.featbit.server.Evaluator.REASON_FLAG_NOT_FOUND;
import static co.featbit.server.Evaluator.REASON_USER_NOT_SPECIFIED;
import static co.featbit.server.Evaluator.REASON_WRONG_TYPE;
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

    private final Consumer<InsightTypes.Event> eventHandler;

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
        checkArgument(Base64.isBase64(envSecret), "envSecret is invalid");
        checkArgument(Utils.isUrl(config.getStreamingURL()), "streaming uri is invalid");
        checkArgument(Utils.isUrl(config.getEventURL()), "event uri is invalid");
        this.offline = config.isOffline();
        ContextImp context = new ContextImp(envSecret, config);
        //init components
        //Insight processor
        this.insightProcessor = config.getInsightProcessorFactory().createInsightProcessor(context);
        this.eventHandler = this.insightProcessor::send;
        //data storage
        this.storage = config.getDataStorageFactory().createDataStorage(context);
        //evaluator
        Evaluator.Getter<DataModel.FeatureFlag> flagGetter = key -> {
            DataStoreTypes.Item item = this.storage.get(DataStoreTypes.FEATURES, key);
            return item == null ? null : (DataModel.FeatureFlag) item;
        };
        Evaluator.Getter<DataModel.Segment> segmentGetter = key -> {
            DataStoreTypes.Item item = this.storage.get(DataStoreTypes.SEGMENTS, key);
            return item == null ? null : (DataModel.Segment) item;
        };
        this.evaluator = new EvaluatorImp(flagGetter, segmentGetter);
        //data updator
        Status.DataUpdaterImpl dataUpdatorImpl = new Status.DataUpdaterImpl(this.storage);
        this.dataUpdater = dataUpdatorImpl;
        //data processor
        this.dataSynchronizer = config.getUpdateProcessorFactory().createUpdateProcessor(context, dataUpdatorImpl);
        //data update status provider
        this.dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdatorImpl);

        // data sync
        Duration startWait = config.getStartWaitTime();
        Future<Boolean> initFuture = this.dataSynchronizer.start();
        if (!startWait.isZero() && !startWait.isNegative()) {
            try {
                if (!(config.getUpdateProcessorFactory() instanceof FactoryImp.NullDataSynchronizerFactory)) {
                    logger.info("FFC JAVA SDK: waiting for Client initialization in {} milliseconds", startWait.toMillis());
                }
                if (config.getDataStorageFactory() instanceof FactoryImp.NullDataStorageFactory) {
                    logger.info("FFC JAVA SDK: SDK just returns default variation");
                }
                boolean initResult = initFuture.get(startWait.toMillis(), TimeUnit.MILLISECONDS);
                if (initResult && !offline) {
                    logger.info("FFC JAVA SDK: the initialization completed");
                }
            } catch (TimeoutException e) {
                logger.error("FFC JAVA SDK: timeout encountered when waiting for data update");
            } catch (Exception e) {
                logger.error("FFC JAVA SDK: exception encountered when waiting for data update", e);
            }

            if (!this.storage.isInitialized() && !offline) {
                logger.info("FFC JAVA SDK: SDK was not successfully initialized");
            }
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
    public FlagState<String> variationDetail(String featureFlagKey, FBUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, null);
        return EvalDetail.of(res.getValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public boolean boolVariation(String featureFlagKey, FBUser user, Boolean defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Boolean.class);
        return BooleanUtils.toBoolean(res.getValue());
    }

    @Override
    public boolean isEnabled(String featureFlagKey, FBUser user) {
        return boolVariation(featureFlagKey, user, false);
    }

    @Override
    public FlagState<Boolean> boolVariationDetail(String featureFlagKey, FBUser user, Boolean defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Boolean.class);
        return EvalDetail.of(BooleanUtils.toBoolean(res.getValue()), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    public double doubleVariation(String featureFlagKey, FBUser user, Double defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Double.class);
        return Double.parseDouble(res.getValue());
    }


    @Override
    public FlagState<Double> doubleVariationDetail(String featureFlagKey, FBUser user, Double defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Double.class);
        return EvalDetail.of(Double.parseDouble(res.getValue()), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    public int intVariation(String featureFlagKey, FBUser user, Integer defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Integer.class);
        return Double.valueOf(res.getValue()).intValue();
    }

    @Override
    public FlagState<Integer> intVariationDetail(String featureFlagKey, FBUser user, Integer defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Integer.class);
        return EvalDetail.of(Double.valueOf(res.getValue()).intValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    public long longVariation(String featureFlagKey, FBUser user, Long defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Long.class);
        return Double.valueOf(res.getValue()).longValue();
    }

    @Override
    public FlagState<Long> longVariationDetail(String featureFlagKey, FBUser user, Long defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, Long.class);
        return EvalDetail.of(Double.valueOf(res.getValue()).longValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public <T> T jsonVariation(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue) {
        String json = variation(featureFlagKey, user, DEFAULT_JSON_VALUE);
        if (DEFAULT_JSON_VALUE.equals(json)) return defaultValue;
        try {
            return JsonHelper.deserialize(json, clazz);
        } catch (JsonParseException ex) {
            logger.error("FFC JAVA SDK: json value can't be parsed", ex);
            return defaultValue;
        }

    }

    @Override
    public <T> FlagState<T> jsonVariationDetail(String featureFlagKey, FBUser user, Class<T> clazz, T defaultValue) {
        T value;
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, DEFAULT_JSON_VALUE, null);
        if (DEFAULT_JSON_VALUE.equals(res.getValue())) {
            value = defaultValue;
        } else {
            try {
                value = JsonHelper.deserialize(res.getValue(), clazz);
            } catch (JsonParseException ex) {
                logger.error("FFC JAVA SDK: unexpected error in evaluation", ex);
                value = defaultValue;
            }
        }
        return EvalDetail.of(value, res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    private Evaluator.EvalResult evaluateInternal(String featureFlagKey, FBUser user, Object defaultValue, Class<?> requiredType) {
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: evaluation is called before Java SDK client is initialized for feature flag, well using the default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_CLIENT_NOT_READY, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (StringUtils.isBlank(featureFlagKey)) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null feature flag key; returning default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            DataModel.FeatureFlag flag = getFlagInternal(featureFlagKey);
            if (flag == null) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: unknown feature flag {}; returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null user for feature flag {}, returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_USER_NOT_SPECIFIED, featureFlagKey, FLAG_NAME_UNKNOWN);
            }

            InsightTypes.Event event = InsightTypes.FlagEvent.of(user);
            Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
            if (requiredType != null && !Utils.checkType(flag.getVariationType(), requiredType, res.getValue())) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: evaluation result {} didn't matched expected type {}", res.getValue(), requiredType);
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_WRONG_TYPE, res.getKeyName(), res.getName());
            }
            eventHandler.accept(event);
            return res;
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in evaluation", ex);
            return Evaluator.EvalResult.error(defaultValue.toString(), REASON_ERROR, featureFlagKey, FLAG_NAME_UNKNOWN);
        }

    }

    private DataModel.FeatureFlag getFlagInternal(String featureFlagKey) {
        DataStoreTypes.Item item = storage.get(DataStoreTypes.FEATURES, featureFlagKey);
        return item == null ? null : (DataModel.FeatureFlag) item;
    }

    public boolean isFlagKnown(String featureKey) {
        try {
            if (!isInitialized()) {
                logger.warn("FFC JAVA SDK: isFlagKnown is called before Java SDK client is initialized for feature flag");
                return false;
            }
            return getFlagInternal(featureKey) == null;
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in isFlagKnown", ex);
        }
        return false;

    }


    public void close() throws IOException {
        logger.info("FFC JAVA SDK: Java SDK client is closing...");
        this.storage.close();
        this.dataSynchronizer.close();
        this.insightProcessor.close();
    }

    public boolean isOffline() {
        return offline;
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
                Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allDataInStorageType = allData.toStorageType();
                boolean res = dataUpdater.init(allDataInStorageType, version);
                if (res) {
                    dataUpdater.updateStatus(Status.StateType.OK, null);
                }
                return res;
            }
        }
        return false;
    }

    @Override
    public AllFlagStates<String> getAllLatestFlagsVariations(FBUser user) {
        ImmutableMap.Builder<EvalDetail<String>, InsightTypes.Event> builder = ImmutableMap.builder();
        boolean success = true;
        String errorString = null;
        EvalDetail<String> ed;
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: Evaluation is called before Java SDK client is initialized for feature flag");
                ed = EvalDetail.of(FLAG_VALUE_UNKNOWN, NO_EVAL_RES, REASON_CLIENT_NOT_READY, FLAG_KEY_UNKNOWN, FLAG_NAME_UNKNOWN);
                builder.put(ed, InsightTypes.NullEvent.INSTANCE);
                success = false;
                errorString = REASON_CLIENT_NOT_READY;
            } else if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null user or feature flag");
                ed = EvalDetail.of(FLAG_VALUE_UNKNOWN, NO_EVAL_RES, REASON_USER_NOT_SPECIFIED, FLAG_KEY_UNKNOWN, FLAG_NAME_UNKNOWN);
                builder.put(ed, InsightTypes.NullEvent.INSTANCE);
                success = false;
                errorString = REASON_USER_NOT_SPECIFIED;
            } else {
                Map<String, DataStoreTypes.Item> allFlags = this.storage.getAll(DataStoreTypes.FEATURES);
                for (DataStoreTypes.Item item : allFlags.values()) {
                    InsightTypes.Event event = InsightTypes.FlagEvent.of(user);
                    DataModel.FeatureFlag flag = (DataModel.FeatureFlag) item;
                    Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
                    ed = EvalDetail.of(res.getValue(), res.getIndex(), res.getReason(), res.getKeyName(), res.getName());
                    builder.put(ed, event);
                }
            }
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in evaluation", ex);
            ed = EvalDetail.of(FLAG_VALUE_UNKNOWN, NO_EVAL_RES, REASON_ERROR, FLAG_KEY_UNKNOWN, FLAG_NAME_UNKNOWN);
            builder.put(ed, InsightTypes.NullEvent.INSTANCE);
            success = false;
            errorString = REASON_ERROR;
        }
        return new Implicits.ComplexAllFlagStates<>(success, errorString, builder.build(), eventHandler);
    }

    @Override
    public void flush() {
        this.insightProcessor.flush();
    }

    @Override
    public void trackMetric(FBUser user, String eventName) {
        trackMetric(user, eventName, 1.0);
    }

    @Override
    public void trackMetric(FBUser user, String eventName, double metricValue) {
        if (user == null || StringUtils.isBlank(eventName) || metricValue <= 0) {
            Loggers.CLIENT.warn("FFC JAVA SDK: event/user/metric invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user).add(InsightTypes.Metric.of(eventName, metricValue));
        insightProcessor.send(event);
    }

    @Override
    public void trackMetrics(FBUser user, String... eventNames) {
        if (user == null || eventNames == null || eventNames.length == 0) {
            Loggers.CLIENT.warn("FFC JAVA SDK: user/events invalid");
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
            Loggers.CLIENT.warn("FFC JAVA SDK: user/metrics invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user);
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String eventName = entry.getKey();
            Double metricValue = entry.getValue();
            if (StringUtils.isNotBlank(eventName) && metricValue != null && metricValue > 0D) {
                event.add(InsightTypes.Metric.of(eventName, metricValue));
            }
        }
        insightProcessor.send(event);
    }

}
