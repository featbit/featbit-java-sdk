package co.featbit.server;

public interface EventBroadcaster<Listener, Event> {
    void addListener(Listener listener);

    void removeListener(Listener listener);

    boolean hasListeners();

    void broadcast(Event event);
}
