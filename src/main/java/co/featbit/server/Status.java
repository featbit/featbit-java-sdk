package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import co.featbit.server.exterior.DataSynchronizer;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public abstract class Status {

    public static final String DATA_STORAGE_INIT_ERROR = "Data Storage init error";
    public static final String DATA_STORAGE_UPDATE_ERROR = "Data Storage update error";
    public static final String REQUEST_INVALID_ERROR = "Request invalid";
    public static final String DATA_INVALID_ERROR = "Received Data invalid";
    public static final String NETWORK_ERROR = "Network error";
    public static final String RUNTIME_ERROR = "Runtime error";
    public static final String UNKNOWN_ERROR = "Unknown error";
    public static final String UNKNOWN_CLOSE_CODE = "Unknown close code";
    public static final String WEBSOCKET_ERROR = "WebSocket error";

    static final String BROADCASTING_IGNORED_FLAG_KEYS = "broacasting_ingnored_flag_keys";

    /**
     * possible values for {@link DataSynchronizer}
     */
    public enum StateType {
        /**
         * The initial state of the update processing when the SDK is being initialized.
         * <p>
         * If it encounters an error that requires it to retry initialization, the state will remain at
         * INITIALIZING until it either succeeds and becomes {@link #OK}, or permanently fails and
         * becomes {@link #OFF}.
         */
        INITIALIZING,
        /**
         * Indicates that the update processing is currently operational and has not had any problems since the
         * last time it received data.
         * <p>
         * In streaming mode, this means that there is currently an open stream connection and that at least
         * one initial message has been received on the stream.
         */
        OK,
        /**
         * Indicates that the update processing encountered an error that it will attempt to recover from.
         * <p>
         * In streaming mode, this means that the stream connection failed, or had to be dropped due to some
         * other error, and will be retried after a backoff delay.
         */
        INTERRUPTED,
        /**
         * Indicates that the update processing has been permanently shut down.
         * <p>
         * This could be because it encountered an unrecoverable error or because the SDK client was
         * explicitly shut down.
         */
        OFF
    }

    public static class ErrorTrack implements Serializable {
        private final String errorType;
        private final String message;

        private ErrorTrack(String errorType, String message) {
            this.errorType = errorType;
            this.message = message;
        }

        public static ErrorTrack of(String errorType, String message) {
            return new ErrorTrack(errorType, message);
        }

        public static ErrorTrack of(String errorType) {
            return new ErrorTrack(errorType, null);
        }

        public String getErrorType() {
            return errorType;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ErrorTrack errorTrack = (ErrorTrack) o;
            return Objects.equals(errorType, errorTrack.errorType) && Objects.equals(message, errorTrack.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorType, message);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("errorType", errorType).add("message", message).toString();
        }
    }

    public static final class State implements Serializable {
        private final StateType stateType;
        private final Instant stateSince;
        private final ErrorTrack errorTrack;

        private State(StateType stateType, Instant stateSince, ErrorTrack errorTrack) {
            this.stateType = stateType;
            this.stateSince = stateSince;
            this.errorTrack = errorTrack;
        }

        public static State initializingState() {
            return new State(StateType.INITIALIZING, Instant.now(), null);
        }

        public static State OKState() {
            return new State(StateType.OK, Instant.now(), null);
        }

        public static State normalOFFState() {
            return new State(StateType.OFF, Instant.now(), null);
        }

        public static State errorOFFState(String errorType, String message) {
            return new State(StateType.OFF, Instant.now(), ErrorTrack.of(errorType, message));
        }

        public static State interruptedState(String errorType, String message) {
            return new State(StateType.INTERRUPTED, Instant.now(), ErrorTrack.of(errorType, message));
        }

        public static State interruptedState(ErrorTrack errorTrack) {
            return new State(StateType.INTERRUPTED, Instant.now(), errorTrack);
        }

        public static State of(StateType stateType, Instant stateSince, String errorType, String message) {
            return new State(stateType, stateSince, ErrorTrack.of(errorType, message));
        }

        public StateType getStateType() {
            return stateType;
        }

        public Instant getStateSince() {
            return stateSince;
        }

        public ErrorTrack getErrorTrack() {
            return errorTrack;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("stateType", stateType).add("stateSince", stateSince).add("info", errorTrack).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return stateType == state.stateType && Objects.equals(stateSince, state.stateSince) && Objects.equals(errorTrack, state.errorTrack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stateType, stateSince, errorTrack);
        }
    }

    /**
     * Interface that {@link DataSynchronizer} implementation will use to push data into the SDK.
     * <p>
     * The {@link DataSynchronizer} interacts with this object, rather than manipulating the {@link DataStorage} directly,
     * so that the SDK can perform any other necessary operations that should perform around data updating.
     * <p>
     * if you overwrite our default DataSynchronizer, you should integrate{@link DataUpdater} to push data
     * and maintain the processor status in your own code, but note that the implementation of this interface is not public
     */
    public interface DataUpdater {

        /**
         * Retrieves all items from the specified collection.
         * <p>
         * If the store contains placeholders for deleted items, it should filter them in the results.
         *
         * @param category specifies which collection to use
         * @return a map of ids and their versioned items
         */
        Map<String, DataStorageTypes.Item> getAll(DataStorageTypes.Category category);

        /**
         * Overwrites the storage with a set of items for each collection, if the new version > the old one
         * <p>
         * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
         * and set the data source state to {@link StateType#INTERRUPTED}.It will not rethrow the error to other level
         * but will simply return {@code false} to indicate that the operation failed.
         *
         * @param allData map of {@link DataStorageTypes.Category} and their data set {@link DataStorageTypes.Item}
         * @param version the version of dataset, Ordinarily it's a timestamp.
         * @return true if the update succeeded
         */
        boolean init(Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData, Long version);

        /**
         * Updates or inserts an item in the specified collection. For updates, the object will only be
         * updated if the existing version is less than the new version; for inserts, if the version > the existing one, it will replace
         * the existing one.
         * <p>
         * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
         * and set the data source state to {@link StateType#INTERRUPTED}.It will not rethrow the error to other level
         * but will simply return {@code false} to indicate that the operation failed.
         *
         * @param category specifies which collection to use
         * @param key      the unique key of the item in the collection
         * @param item     the item to insert or update
         * @param version  the version of item
         * @return true if success
         */
        boolean upsert(DataStorageTypes.Category category, String key, DataStorageTypes.Item item, Long version);

        /**
         * Informs the SDK of a change in the {@link DataSynchronizer} status.
         * <p>
         * {@link DataSynchronizer} implementations should use this method
         * if they have any concept of being in a valid state, a temporarily disconnected state, or a permanently stopped state.
         * <p>
         * If {@code newState} is different from the previous state, and/or {@code newError} is non-null, the
         * SDK will start returning the new status (adding a timestamp for the change) from
         * {@link DataUpdateStatusProvider#getState()}.
         * <p>
         * A special case is that if {@code newState} is {@link StateType#INTERRUPTED},
         * but the previous state was {@link StateType#INITIALIZING}, the state will remain at {@link StateType#INITIALIZING}
         * because {@link StateType#INTERRUPTED} is only meaningful after a successful startup.
         *
         * @param newState the new state of {@link DataSynchronizer}
         */
        void updateStatus(State newState);

        /**
         * return the latest version of {@link DataStorage}
         *
         * @return a long value
         */
        long getVersion();

        /**
         * return true if the {@link DataStorage} is well initialized
         *
         * @return true if the {@link DataStorage} is well initialized
         */
        boolean storageInitialized();

        /**
         * get flag change event notifier {@link EventBroadcaster}
         *
         * @return EventBroadcaster
         */
        EventBroadcaster<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> getFlagChangeEventNotifier();


    }

    /**
     * The {@link DataSynchronizer} will push updates into this component. This component
     * then apply necessary transformations, like status management(checking, updating, notifying etc.), failure tracking,
     * before putting updating items into data storage.
     * <p>
     * This component is thread safe and is basic component usd in bootstrapping.
     */
    static final class DataUpdaterImpl implements DataUpdater {
        private final DataStorage storage;
        private volatile State currentState;
        private final Object lockObject = new Object();

        private final EventBroadcasterImpl<StateListener, State> dataUpdateStateNotifier;
        private final EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier;

        public DataUpdaterImpl(DataStorage storage,
                               EventBroadcasterImpl<StateListener, State> dataUpdateStateNotifier,
                               EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier) {
            this.storage = storage;
            this.dataUpdateStateNotifier = dataUpdateStateNotifier;
            this.flagChangeEventNotifier = flagChangeEventNotifier;
            this.currentState = State.initializingState();
        }

        // just use for test
        DataUpdaterImpl(DataStorage storage,
                        State state,
                        EventBroadcasterImpl<StateListener, State> dataUpdateStateNotifier,
                        EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier) {
            this.storage = storage;
            this.currentState = state;
            this.dataUpdateStateNotifier = dataUpdateStateNotifier;
            this.flagChangeEventNotifier = flagChangeEventNotifier;
        }

        private void handleErrorFromStorage(Exception ex, ErrorTrack errorTrack) {
            Loggers.DATA_STORAGE.error("FB JAVA SDK: Data Storage error: {}, UpdateProcessor will attempt to receive the data", ex.getMessage());
            updateStatus(State.interruptedState(errorTrack));
        }

        @Override
        public Map<String, DataStorageTypes.Item> getAll(DataStorageTypes.Category category) {
            return storage.getAll(category);
        }

        @Override
        public boolean init(Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData, Long version) {
            try {
                // if storage is empty, synchronized data in using upsert method
                storage.init(allData, version);
            } catch (Exception ex) {
                handleErrorFromStorage(ex, ErrorTrack.of(DATA_STORAGE_INIT_ERROR, ex.getMessage()));
                return false;
            }
            return true;
        }

        @Override
        public boolean upsert(DataStorageTypes.Category category, String key, DataStorageTypes.Item item, Long version) {
            boolean updated = false;
            try {
                updated = storage.upsert(category, key, item, version);
            } catch (Exception ex) {
                handleErrorFromStorage(ex, ErrorTrack.of(DATA_STORAGE_UPDATE_ERROR, ex.getMessage()));
                return false;
            }
            return updated;
        }

        @Override
        public void updateStatus(State newState) {
            if (newState == null) {
                return;
            }

            State stateToNotify = null;

            synchronized (lockObject) {
                StateType oldStateType = currentState.getStateType();
                StateType newStateType = newState.getStateType();
                ErrorTrack error = newState.getErrorTrack();
                // interrupted state is only meaningful after initialization
                if (newStateType == StateType.INTERRUPTED && oldStateType == StateType.INITIALIZING) {
                    newStateType = StateType.INITIALIZING;
                }

                if (newStateType != oldStateType || error != null) {
                    Instant stateSince = (newStateType != oldStateType) ? Instant.now() : currentState.getStateSince();
                    currentState = new State(newStateType, stateSince, error);
                    stateToNotify = currentState;
                    lockObject.notifyAll();
                }
            }

            if (stateToNotify != null && dataUpdateStateNotifier.hasListeners()) {
                dataUpdateStateNotifier.broadcast(stateToNotify);
            }

        }

        @Override
        public long getVersion() {
            return storage.getVersion();
        }

        @Override
        public boolean storageInitialized() {
            return storage.isInitialized();
        }

        @Override
        public EventBroadcaster<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> getFlagChangeEventNotifier() {
            return this.flagChangeEventNotifier;
        }

        // blocking util you get the desired state, time out reaches or thread is interrupted
        boolean waitFor(StateType state, Duration timeout) throws InterruptedException {
            Duration timeout1 = timeout == null ? Duration.ZERO : timeout;
            Instant deadline = Instant.now().plus(timeout1);
            synchronized (lockObject) {
                while (true) {
                    StateType curr = currentState.getStateType();
                    if (curr == state) {
                        return true;
                    }
                    if (curr == StateType.OFF) {
                        return false;
                    }
                    if (timeout1.isZero() || timeout1.isNegative()) {
                        lockObject.wait();
                    } else {
                        // block the consumer thread util getting desired state
                        // or quitting in timeout
                        Instant now = Instant.now();
                        if (now.isAfter(deadline)) {
                            return false;
                        }
                        Duration rest = Duration.between(now, deadline);
                        lockObject.wait(rest.toMillis(), 1);
                    }
                }
            }
        }

        State getCurrentState() {
            synchronized (lockObject) {
                return currentState;
            }
        }

    }

    /**
     * An interface to query the status of a {@link DataSynchronizer}
     * With the build-in implementation, this might be useful if you want to use SDK without waiting for it to initialize
     */
    public interface DataUpdateStatusProvider {

        /**
         * Returns the current status of the {@link DataSynchronizer}
         * <p>
         * All of the {@link DataSynchronizer} implementations are guaranteed to update this status
         * whenever they successfully initialize, encounter an error, or recover after an error.
         * <p>
         * For a custom implementation, it is the responsibility of the data source to report its status via {@link DataUpdater};
         * if it does not do so, the status will always be reported as {@link StateType#INITIALIZING}.
         *
         * @return the latest status; will never be null
         */
        State getState();

        /**
         * A method for waiting for a desired connection state after bootstrapping
         * <p>
         * If the current state is already {@code desiredState} when this method is called, it immediately returns.
         * Otherwise, it blocks until 1. the state has become {@code desiredState}, 2. the state has become
         * {@link StateType#OFF} , 3. the specified timeout elapses, or 4. the current thread is deliberately interrupted with {@link Thread#interrupt()}.
         * <p>
         * A scenario in which this might be useful is if you want to use SDK without waiting
         * for it to initialize, and then wait for initialization at a later time or on a different point:
         * <pre><code>
         *     FBConfig config = new FBConfig.Builder()
         *         .startWait(Duration.ZERO)
         *         .build();
         *     FBClient client = new FBClient(sdkKey, config);
         *
         *     // later, when you want to wait for initialization to finish:
         *     boolean inited = client.getDataUpdateStatusProvider().waitFor(StateType.OK, Duration.ofSeconds(15))
         *     if (!inited) {
         *         // do whatever is appropriate if initialization has timed out
         *     }
         * </code></pre>
         *
         * @param state   the desired connection state (normally this would be {@link StateType#OK})
         * @param timeout the maximum amount of time to wait-- or {@link Duration#ZERO} to block indefinitely
         *                (unless the thread is explicitly interrupted)
         * @return true if the connection is now in the desired state; false if it timed out, or if the state
         * changed to 2 and that was not the desired state
         * @throws InterruptedException if {@link Thread#interrupt()} was called on this thread while blocked
         */
        boolean waitFor(StateType state, Duration timeout) throws InterruptedException;

        /**
         * alias of {@link #waitFor(StateType, Duration)} in {@link StateType#OK}
         *
         * @param timeout the maximum amount of time to wait-- or {@link Duration#ZERO} to block indefinitely
         *                (unless the thread is explicitly interrupted)
         * @return true if the connection is now in {@link StateType#OK}; false if it timed out, or if the state
         * changed to {@link StateType#OFF} and that was not the desired state
         * @throws InterruptedException throws an InterruptedException
         */
        boolean waitForOKState(Duration timeout) throws InterruptedException;

        void addStateListener(StateListener listener);

        void removeStateListener(StateListener listener);

    }

    static final class DataUpdateStatusProviderImpl implements DataUpdateStatusProvider {

        private final DataUpdaterImpl dataUpdater;

        private final EventBroadcasterImpl<StateListener, State> dataUpdateStateNotifier;

        public DataUpdateStatusProviderImpl(DataUpdaterImpl dataUpdater, EventBroadcasterImpl<StateListener, State> dataUpdateStateNotifier) {
            this.dataUpdater = dataUpdater;
            this.dataUpdateStateNotifier = dataUpdateStateNotifier;
        }

        @Override
        public State getState() {
            return dataUpdater.getCurrentState();
        }

        @Override
        public boolean waitFor(StateType state, Duration timeout) throws InterruptedException {
            return dataUpdater.waitFor(state, timeout);
        }

        @Override
        public boolean waitForOKState(Duration timeout) throws InterruptedException {
            return waitFor(StateType.OK, timeout);
        }

        @Override
        public void addStateListener(StateListener listener) {
            dataUpdateStateNotifier.addListener(listener);
        }

        @Override
        public void removeStateListener(StateListener listener) {
            dataUpdateStateNotifier.removeListener(listener);
        }
    }

    /**
     * Interface for receiving data updating state change notifications.
     */
    public interface StateListener {
        /**
         * Called when the data updating state has changed.
         *
         * @param newState the new state
         */
        void onStateChange(State newState);
    }

}
