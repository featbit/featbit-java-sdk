package co.featbit.server.exterior;

import java.io.Closeable;
import java.util.Map;

/**
 * Interface for a data storage that holds feature flags, user segments or any other related data received by the SDK.
 * <p>
 * Ordinarily, the only implementations of this interface are the default in-memory implementation,
 * which holds references to actual SDK data model objects.
 * <p>
 * All implementations should permit concurrent access and updates.
 */
public interface DataStorage extends Closeable {
    /**
     * Overwrites the storage with a set of items for each collection, if the new version > the old one
     * <p>
     *
     * @param allData map of {@link DataStorageTypes.Category} and their data set {@link DataStorageTypes.Item}
     * @param version the version of dataset, Ordinarily it's a timestamp.
     */
    boolean init(Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData, Long version);

    /**
     * Retrieves an item from the specified collection, if available.
     * <p>
     * If the item has been achieved and the store contains an achieved placeholder, but it should return null
     *
     * @param category specifies which collection to use
     * @param key      the unique key of the item in the collection
     * @return a versioned item that contains the stored data or null if item is deleted or unknown
     */
    DataStorageTypes.Item get(DataStorageTypes.Category category, String key);

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
     * Updates or inserts an item in the specified collection. For updates, the object will only be
     * updated if the existing version is less than the new version; for inserts, if the version > the existing one, it will replace
     * the existing one.
     * <p>
     * The SDK may pass an {@link DataStorageTypes.Item} that contains an archived object,
     * In that case, assuming the version is greater than any existing version of that item, the store should retain
     * a placeholder rather than simply not storing anything.
     *
     * @param category specifies which collection to use
     * @param key      the unique key of the item in the collection
     * @param item     the item to insert or update
     * @param version  the version of item
     * @return true if success
     */
    boolean upsert(DataStorageTypes.Category category,
                   String key,
                   DataStorageTypes.Item item,
                   Long version);

    /**
     * Checks whether this store has been initialized with any data yet.
     *
     * @return true if the storage contains data
     */
    boolean isInitialized();

    /**
     * return the latest version of storage
     *
     * @return a long value
     */
    long getVersion();
}