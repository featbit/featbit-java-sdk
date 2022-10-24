package co.featbit.server.exterior;

import co.featbit.server.InsightTypes;
import co.featbit.server.Factory;

import java.io.Closeable;

/**
 * Interface for a component to send analytics events.
 * <p>
 * The standard implementations are:
 * <ul>
 * <li>{@link Factory#insightProcessorFactory()} (the default), which
 * sends events to feature flag center
 * <li>{@link Factory#externalEventTrack()} which does nothing
 * (on the assumption that another process will send the events);
 * </ul>
 *
 */
public interface InsightProcessor extends Closeable {

    /**
     * Records an event asynchronously.
     *
     * @param event
     */
    void send(InsightTypes.Event event);

    /**
     * Specifies that any buffered events should be sent as soon as possible, rather than waiting
     * for the next flush interval. This method is asynchronous, so events still may not be sent
     * until a later time. However, calling {@link Closeable#close()} will synchronously deliver
     * any events that were not yet delivered prior to shutting down.
     */
    void flush();
}
