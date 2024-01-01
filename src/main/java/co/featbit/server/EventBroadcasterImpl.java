package co.featbit.server;

import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

class EventBroadcasterImpl<Listener, Event> implements EventBroadcaster<Listener, Event> {
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final BiConsumer<Listener, Event> broadcaster;
    private final ExecutorService executor;
    private final Logger logger;

    EventBroadcasterImpl(
            BiConsumer<Listener, Event> broadcaster,
            ExecutorService executor,
            Logger logger
    ) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.logger = logger;
    }

    static EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> forFlagChangeEvents(
            ExecutorService executor, Logger logger) {
        return new EventBroadcasterImpl<>(FlagChange.FlagChangeListener::onFlagChange, executor, logger);
    }

    static EventBroadcasterImpl<Status.StateListener, Status.State> forDataUpdateStates(
            ExecutorService executor, Logger logger) {
        return new EventBroadcasterImpl<>(Status.StateListener::onStateChange, executor, logger);
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    @Override
    public void broadcast(Event event) {
        if (executor == null) {
            return;
        }
        for (Listener listener : listeners) {
            executor.submit(() -> {
                try {
                    broadcaster.accept(listener, event);
                } catch (Exception e) {
                    logger.error("Unexpected exception in event listener", e);
                }
            });
        }
    }


}
