package co.featbit.server.exterior;

/**
 * Interface for a factory that creates some implementation of {@link DataStorage}.
 *
 */
public interface DataStorageFactory {

    /**
     * Creates an implementation.
     *
     * @param context allows access to the client configuration
     * @return a {@link DataStorage}
     */
    DataStorage createDataStorage(Context context);
}
