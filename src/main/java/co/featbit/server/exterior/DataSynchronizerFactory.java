package co.featbit.server.exterior;

import co.featbit.server.Factory;
import co.featbit.server.Status;

/**
 * Interface for a factory that creates some implementation of {@link DataSynchronizer}.
 *
 * @see Factory
 */
public interface DataSynchronizerFactory {
    /**
     * Creates an implementation instance.
     *
     * @param context     allows access to the client configuration
     * @param dataUpdater the {@link Status.DataUpdater} which pushes data into the {@link DataStorage}
     * @return an {@link DataSynchronizer}
     */
    DataSynchronizer createUpdateProcessor(Context context, Status.DataUpdater dataUpdater);
}
