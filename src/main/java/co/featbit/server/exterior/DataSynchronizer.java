package co.featbit.server.exterior;

import co.featbit.server.Factory;

import java.io.Closeable;
import java.util.concurrent.Future;

/**
 * Interface to receive updates to feature flags, user segments, and anything
 * else that might come from feature flag center, and passes them to a {@link DataStorage}.
 * <p>
 * The standard implementations are:
 * <ul>
 * <li> {@link Factory#dataSynchronizerFactory()} (the default), which
 * maintains a streaming connection;
 * <li> {@link Factory#externalDataSynchronization()}, which does nothing
 * (on the assumption that another process will update the data store);
 * </ul>
 */
public interface DataSynchronizer extends Closeable {

    /**
     * Starts the client update processing.
     *
     * @return {@link Future}'s completion status indicates the client has been initialized.
     */
    Future<Boolean> start();

    /**
     *  Returns true once the client has been initialized and will never return false again.
     * @return true if the client has been initialized
     */
    boolean isInitialized();
}
