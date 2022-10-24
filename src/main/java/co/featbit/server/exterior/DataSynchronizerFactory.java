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
     * @param dataUpdator the {@link Status.DataUpdator} which pushes data into the {@link DataStorage}
     * @return an {@link DataSynchronizer}
     */
    DataSynchronizer createUpdateProcessor(Context context, Status.DataUpdator dataUpdator);
}
