package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * A thread-safe, versioned storage for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link DataStorage}.
 */

final class InMemoryDataStorage implements DataStorage {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private boolean initialized = false;
    private Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of();
    private long version = 0;

    InMemoryDataStorage() {
        super();
    }

    @Override
    public boolean init(Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData, Long version) {
        if (version == null || this.version >= version || allData == null || allData.isEmpty()) {
            return false;
        }

        rwLock.writeLock().lock();
        try {
            this.allData = ImmutableMap.copyOf(allData);
            initialized = true;
            this.version = version;
            Loggers.DATA_STORAGE.debug("Data storage initialized");
        } finally {
            rwLock.writeLock().unlock();
        }
        return true;
    }

    @Override
    public DataStorageTypes.Item get(DataStorageTypes.Category category, String key) {
        rwLock.readLock().lock();
        try {
            Map<String, DataStorageTypes.Item> items = allData.get(category);
            if (items == null) return null;
            DataStorageTypes.Item item = items.get(key);
            if (item == null || item.isArchived()) return null;
            return item;
        } finally {
            rwLock.readLock().unlock();
        }

    }

    @Override
    public Map<String, DataStorageTypes.Item> getAll(DataStorageTypes.Category category) {
        rwLock.readLock().lock();
        try {
            Map<String, DataStorageTypes.Item> items = allData.get(category);
            if (items == null) return ImmutableMap.of();
            Map<String, DataStorageTypes.Item> map = items.entrySet().stream().filter(entry -> !entry.getValue().isArchived()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ImmutableMap.copyOf(map);
        } finally {
            rwLock.readLock().unlock();
        }

    }


    @Override
    public boolean upsert(DataStorageTypes.Category category, String key, DataStorageTypes.Item item, Long version) {
        if (version == null || this.version >= version || item == null) {
            return false;
        }
        rwLock.writeLock().lock();
        try {
            Map<String, DataStorageTypes.Item> oldItems = allData.get(category);
            DataStorageTypes.Item oldItem = null;
            if (oldItems != null) {
                oldItem = oldItems.get(key);
                if (oldItem != null && oldItem.getTimestamp() >= item.getTimestamp()) return false;
            }
            // the data cannot change in any way once an instance of the Immutable Map is created.
            // we should re-initialize a new internal map when update
            ImmutableMap.Builder<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> newData = ImmutableMap.builder();
            for (Map.Entry<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> entry : allData.entrySet()) {
                if (!entry.getKey().equals(category)) {
                    newData.put(entry.getKey(), entry.getValue());
                }
            }
            if (oldItems == null) {
                newData.put(category, ImmutableMap.of(key, item));
            } else {
                ImmutableMap.Builder<String, DataStorageTypes.Item> newItems = ImmutableMap.builder();
                if (oldItem == null) {
                    newItems.putAll(oldItems);
                } else {
                    for (Map.Entry<String, DataStorageTypes.Item> entry : oldItems.entrySet()) {
                        if (!entry.getKey().equals(key)) {
                            newItems.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                newItems.put(key, item);
                newData.put(category, newItems.build());
            }
            allData = newData.build();
            this.version = version;
            if (!initialized) initialized = true;
            Loggers.DATA_STORAGE.debug("upsert item {} into storage", key);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    @Override
    public boolean isInitialized() {
        rwLock.readLock().lock();
        try {
            return initialized;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public long getVersion() {
        rwLock.readLock().lock();
        try {
            return version;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
    }
}
