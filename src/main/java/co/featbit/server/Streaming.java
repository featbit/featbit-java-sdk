package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.commons.json.JsonParseException;
import co.featbit.server.exterior.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static co.featbit.server.Status.REQUEST_INVALID_ERROR;
import static co.featbit.server.Status.UNKNOWN_CLOSE_CODE;
import static co.featbit.server.Streaming.StreamingOps.*;

final class Streaming implements DataSynchronizer {

    //constants
    private static final String FULL_OPS = "full";
    private static final String PATCH_OPS = "patch";
    private static final Integer NORMAL_CLOSE = 1000;
    private static final String NORMAL_CLOSE_REASON = "normal close";
    private static final Integer INVALID_REQUEST_CLOSE = 4003;
    private static final String INVALID_REQUEST_CLOSE_REASON = "invalid request";
    private static final Integer GOING_AWAY_CLOSE = 1001;
    private static final String CLOSE_AND_THEN_RECONN_BY_DATASYNC_ERROR = "data sync error";
    private static final Duration PING_INTERVAL = Duration.ofSeconds(10);
    private static final Duration AWAIT_TERMINATION = Duration.ofSeconds(2);
    private static final String AUTH_PARAMS = "?token=%s&type=server&version=2";
    private static final Map<Integer, String> NOT_RECONN_CLOSE_REASON = ImmutableMap.of(NORMAL_CLOSE, NORMAL_CLOSE_REASON, INVALID_REQUEST_CLOSE, INVALID_REQUEST_CLOSE_REASON);
    private static final List<Class<? extends Exception>> WEBSOCKET_EXCEPTION = ImmutableList.of(SocketTimeoutException.class, SocketException.class, EOFException.class);
    private static final Logger logger = Loggers.UPDATE_PROCESSOR;

    // final variables
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean isWSConnected = new AtomicBoolean(false);
    private final AtomicInteger connCount = new AtomicInteger(0);
    private final AtomicBoolean forceToCloseWS = new AtomicBoolean(false);
    private final CompletableFuture<Boolean> initFuture = new CompletableFuture<>();
    private final StreamingWebSocketListener listener = new DefaultWebSocketListener();
    private final ScheduledThreadPoolExecutor pingScheduledExecutor;
    private final Status.DataUpdater updater;
    private final BasicConfig basicConfig;
    private final HttpConfig httpConfig;
    private final Integer maxRetryTimes;
    private final BackoffAndJitterStrategy strategy;
    private final String streamingURI;

    private final OkHttpClient okHttpClient;
    WebSocket webSocket;

    Streaming(Status.DataUpdater updater, Context config, Duration firstRetryDelay, Integer maxRetryTimes) {
        this.updater = updater;
        this.basicConfig = config.basicConfig();
        this.httpConfig = config.http();
        this.streamingURI = config.basicConfig().getStreamingURI();
        this.strategy = new BackoffAndJitterStrategy(firstRetryDelay);
        this.maxRetryTimes = (maxRetryTimes == null || maxRetryTimes <= 0) ? Integer.MAX_VALUE : maxRetryTimes;
        this.okHttpClient = buildWebOkHttpClient();
        this.pingScheduledExecutor = new ScheduledThreadPoolExecutor(1, Utils.createThreadFactory("streaming-periodic-ping-worker-%d", true));
    }

    @Override
    public Future<Boolean> start() {
        logger.debug("Streaming Starting...");
        // flags reset to original state
        connCount.set(0);
        isWSConnected.set(false);
        forceToCloseWS.set(false);
        connect();
        pingScheduledExecutor.scheduleAtFixedRate(this::ping, 0L, PING_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        return initFuture;
    }

    @Override
    public boolean isInitialized() {
        return initialized.get();
    }

    @Override
    public void close() {
        logger.info("FB JAVA SDK: streaming is stopping...");
        if (webSocket != null) {
            forceToCloseWS.compareAndSet(false, true);
            webSocket.close(NORMAL_CLOSE, NORMAL_CLOSE_REASON);
            if (!isWSConnected.get()) {
                // websocket is not connected
                // force to clean up thread and conn pool
                clearExecutor();
            }
        }
    }

    private void ping() {
        if (webSocket != null && isWSConnected.get() && !forceToCloseWS.get()) {
            logger.trace("ping");
            String json = JsonHelper.serialize(new DataModel.DataSyncMessage(null));
            webSocket.send(json);
        }
    }

    private void clearExecutor() {
        Loggers.UPDATE_PROCESSOR.debug("streaming processor clean up thread and conn pool");
        Utils.shutDownThreadPool("streaming-periodic-ping-worker", pingScheduledExecutor, AWAIT_TERMINATION);
        Utils.shutdownOKHttpClient("Streaming", okHttpClient);
    }

    private void connect() {
        if (isWSConnected.get() || forceToCloseWS.get()) {
            logger.error("FB JAVA SDK: streaming websocket is already Connected or Closed");
            return;
        }
        int count = connCount.getAndIncrement();
        if (count >= maxRetryTimes) {
            logger.error("FB JAVA SDK: streaming websocket have reached max retry");
            return;
        }

        String token = Utils.buildToken(basicConfig.getEnvSecret());
        String url = String.format(streamingURI.concat(AUTH_PARAMS), token);
        Headers headers = Utils.headersBuilderFor(httpConfig).build();
        Request request = new Request.Builder().headers(headers).url(url).build();
        logger.debug("Streaming WebSocket is connecting...");
        strategy.setGoodRunAtNow();
        webSocket = okHttpClient.newWebSocket(request, listener);
    }

    private void reconnect() {
        try {
            Duration delay = strategy.nextDelay(false);
            long delayInMillis = delay.toMillis();
            logger.debug("Streaming WebSocket will reconnect in {} milliseconds", delayInMillis);
            Thread.sleep(delayInMillis);
        } catch (InterruptedException ignore) {
        } finally {
            connect();
        }
    }

    @NotNull
    private OkHttpClient buildWebOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(httpConfig.connectTime()).pingInterval(Duration.ZERO).retryOnConnectionFailure(false);
        Utils.buildProxyAndSocketFactoryFor(builder, httpConfig);
        return builder.build();
    }

    static final class StreamingOps {
        private static void broadcast(Status.DataUpdater updater, Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> updatedData) {
            Set<String> flagKeySet = new HashSet<>();
            if (updater.getFlagChangeEventNotifier().hasListeners()) {
                for (Map.Entry<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> entry : updatedData.entrySet()) {
                    if (DataStorageTypes.FEATURES.equals(entry.getKey())) {
                        for (String id : entry.getValue().keySet()) {
                            if (!flagKeySet.contains(id)) {
                                updater.getFlagChangeEventNotifier().broadcast(new FlagChange.FlagChangeEvent(id));
                                flagKeySet.add(id);
                            }
                        }
                    } else if (DataStorageTypes.SEGMENTS.equals(entry.getKey())) {
                        List<DataModel.FeatureFlag> flags = updater.getAll(DataStorageTypes.FEATURES).values().stream()
                                .map(item -> (DataModel.FeatureFlag) item)
                                .collect(Collectors.toList());
                        for (String sig : entry.getValue().keySet()) {
                            flags.stream()
                                    .filter(flag -> flag.containsSegment(sig) && !flagKeySet.contains(flag.getId()))
                                    .forEach(flag -> {
                                        updater.getFlagChangeEventNotifier().broadcast(new FlagChange.FlagChangeEvent(flag.getId()));
                                        flagKeySet.add(flag.getId());
                                    });
                        }
                    }
                }
            }
        }

        static Boolean processData(Status.DataUpdater updater, DataModel.Data data, AtomicBoolean initialized, CompletableFuture<Boolean> initFuture) {
            boolean opOK = false;
            String eventType = data.getEventType();
            Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> updatedData = data.toStorageType();
            if (FULL_OPS.equalsIgnoreCase(eventType)) {
                opOK = updater.init(updatedData, data.getTimestamp());
            } else if (PATCH_OPS.equalsIgnoreCase(eventType)) {
                // streaming patch is a real time update
                // no data update is considered as a good operation
                opOK = updatedData.entrySet().stream()
                        .flatMap(entry -> entry.getValue().values().stream().map(item -> ImmutablePair.of(entry.getKey(), item)))
                        .sorted(Comparator.comparingLong(pair -> pair.getRight().getTimestamp()))
                        .collect(Collectors.toList())
                        .stream()
                        .allMatch(pair -> updater.upsert(pair.getLeft(), pair.getRight().getId(), pair.getRight(), pair.getRight().getTimestamp()));
            }
            if (opOK) {
                if (initialized.compareAndSet(false, true)) {
                    initFuture.complete(true);
                }
                logger.debug("processing data is well done");
                updater.updateStatus(Status.State.OKState());
                broadcast(updater, updatedData);
            }
            return opOK;
        }

        static boolean isReconnOnClose(Status.DataUpdater updater, int code, String reason) {
            boolean isReconn = false;
            // close conn if the code is 1000 or 4003
            // any other close code will cause a reconnecting to server
            String message = NOT_RECONN_CLOSE_REASON.get(code);
            if (message == null) {
                isReconn = true;
                message = StringUtils.isEmpty(reason) ? "unexpected close" : reason;
            }
            logger.info("Streaming WebSocket close reason: {}", message);
            if (isReconn && code != GOING_AWAY_CLOSE) {
                // if code is not 1001, it's an unknown close code received by server
                updater.updateStatus(Status.State.interruptedState(UNKNOWN_CLOSE_CODE, message));
            } else if (code == INVALID_REQUEST_CLOSE) {
                // authorization error
                updater.updateStatus(Status.State.errorOFFState(REQUEST_INVALID_ERROR, message));
            } else if (code == NORMAL_CLOSE) {
                // normal close by client peer
                updater.updateStatus(Status.State.normalOFFState());
            }
            return isReconn;
        }

        static boolean isReconnOnFailure(Status.DataUpdater updater, Throwable t) {
            boolean isReconn;
            String errorType;
            Class<? extends Throwable> tClass = t.getClass();
            String message = String.format("%s : %s", tClass.getTypeName(), t.getMessage());
            if (t instanceof RuntimeException) {
                // runtime exception restart except JsonParseException
                isReconn = tClass != JsonParseException.class;
                errorType = isReconn ? Status.RUNTIME_ERROR : Status.DATA_INVALID_ERROR;
            } else {
                isReconn = true;
                if (WEBSOCKET_EXCEPTION.contains(tClass)) {
                    errorType = Status.WEBSOCKET_ERROR;
                } else if (t instanceof IOException) {
                    errorType = Status.NETWORK_ERROR;
                } else {
                    errorType = Status.UNKNOWN_ERROR;
                }
            }
            if (isReconn) {
                logger.warn("FB JAVA SDK: streaming webSocket will reconnect because of {}", t.getMessage());
                updater.updateStatus(Status.State.interruptedState(errorType, message));
            } else {
                logger.error("FB JAVA SDK: streaming webSocket Failure", t);
                updater.updateStatus(Status.State.errorOFFState(errorType, message));
            }
            return isReconn;
        }

    }

    final class DefaultWebSocketListener extends StreamingWebSocketListener {
        // this callback method may throw a JsonParseException
        // if received data is invalid
        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            logger.trace(text);
            DataModel.StreamingMessage message = JsonHelper.deserialize(text, DataModel.StreamingMessage.class);
            if (DataModel.StreamingMessage.DATA_SYNC.equalsIgnoreCase(message.getMessageType())) {
                logger.debug("Streaming WebSocket is processing data");
                DataModel.All all = JsonHelper.deserialize(text, DataModel.All.class);
                if (all.isProcessData() && !processData(updater, all.data(), initialized, initFuture)) {
                    // reconnect to server to get back data after data storage failed
                    // the reason is gathered by DataUpdater
                    // close code 1001 means peer going away
                    webSocket.close(GOING_AWAY_CLOSE, CLOSE_AND_THEN_RECONN_BY_DATASYNC_ERROR);
                }
            }
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            super.onOpen(webSocket, response);
            String json;
            if (updater.storageInitialized()) {
                Long timestamp = updater.getVersion();
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(timestamp));
            } else {
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(0L));
            }
            webSocket.send(json);
        }
    }


    abstract class StreamingWebSocketListener extends WebSocketListener {

        @Override
        public final void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            isWSConnected.compareAndSet(true, false);
            if (isReconnOnClose(updater, code, reason)) {
                reconnect();
            } else {
                // clean up thread and conn pool
                clearExecutor();
            }
        }

        @Override
        public final void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            isWSConnected.compareAndSet(true, false);
            if (isReconnOnFailure(updater, t)) {
                reconnect();
            } else {
                // clean up thread and conn pool
                clearExecutor();
            }
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            logger.debug("Ask Data Updating, http code {}", response.code());
            isWSConnected.compareAndSet(false, true);
        }
    }
}
