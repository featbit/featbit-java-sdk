package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStoreTypes;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static co.featbit.server.exterior.DataStoreTypes.DATATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDataStorageTest {
    private DataStorage dataStorage;

    private DataStoreTypes.Item item1 = new TestDataModel.TestItem(false, "test item 1");
    private final DataStoreTypes.Item item2 = new TestDataModel.TestItem(false, "test item 2");
    private final DataStoreTypes.Item archivedItem = new TestDataModel.TestItem(true, "test archive item");

    @BeforeEach
    void init() {
        dataStorage = new InMemoryDataStorage();
    }

    @Test
    void testDefaultVersion(){
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
    }

    @Test
    void testInit() {
        Map<String, DataStoreTypes.Item> items = ImmutableMap.of(item1.getId(), item1, archivedItem.getId(), archivedItem);
        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData = ImmutableMap.of(DATATEST, items);
        dataStorage.init(allData, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATEST, item1.getId()));
        assertNull(dataStorage.get(DATATEST, archivedItem.getId()));
        assertEquals(1, dataStorage.getAll(DATATEST).size());
    }

    @Test
    void testInvalidInit() {
        dataStorage.init(null, 1L);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData = ImmutableMap.of();
        dataStorage.init(allData, 1L);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        Map<String, DataStoreTypes.Item> items = ImmutableMap.of(item1.getId(), item1);
        allData = ImmutableMap.of(DATATEST, items);
        dataStorage.init(allData, null);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        dataStorage.init(allData, -1L);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());

        dataStorage.init(allData, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        items = ImmutableMap.of(item1.getId(), item1, item2.getId(), item2);
        allData = ImmutableMap.of(DATATEST, items);
        dataStorage.init(allData, 1L);
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(1, dataStorage.getAll(DATATEST).size());
    }

    @Test
    void testGet() {
        Map<String, DataStoreTypes.Item> items = ImmutableMap.of(item1.getId(), item1, archivedItem.getId(), archivedItem);
        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData = ImmutableMap.of(DATATEST, items);
        dataStorage.init(allData, 1L);
        assertEquals(item1, dataStorage.get(DATATEST, item1.getId()));
        assertNull(dataStorage.get(DATATEST, archivedItem.getId()));
        assertEquals(1, dataStorage.getAll(DATATEST).size());
        assertEquals(item1, dataStorage.getAll(DATATEST).get(item1.getId()));
    }

    @Test
    void testUpsert() {
        dataStorage.upsert(DATATEST, item1.getId(), item1, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATEST, item1.getId()));
        dataStorage.upsert(DATATEST, item2.getId(), item2, 2L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(2L, dataStorage.getVersion());
        assertEquals(item2, dataStorage.get(DATATEST, item2.getId()));
        item1 = new TestDataModel.TestItem(item1.getId(), false, "updated test item 1");
        dataStorage.upsert(DATATEST, item1.getId(), item1, 3L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(3L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATEST, item1.getId()));
    }

    @Test
    void testInvalidUpsert() {
        assertFalse(dataStorage.upsert(DATATEST, null, null, null));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        assertFalse(dataStorage.upsert(DATATEST, item1.getId(), item1, null));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        assertFalse(dataStorage.upsert(DATATEST, item1.getId(), item1, -1L));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());

        dataStorage.upsert(DATATEST, item1.getId(), item1, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertFalse(dataStorage.upsert(DATATEST, item2.getId(), item2, 1L));
        assertEquals(1L, dataStorage.getVersion());
    }

}
