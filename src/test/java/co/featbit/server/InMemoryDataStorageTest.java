package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static co.featbit.server.exterior.DataStorageTypes.DATATESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDataStorageTest {
    private DataStorage dataStorage;

    private DataStorageTypes.Item item1 = new TestDataModel.TestItem(false, "test item 1");
    private final DataStorageTypes.Item item2 = new TestDataModel.TestItem(false, "test item 2");
    private final DataStorageTypes.Item archivedItem = new TestDataModel.TestItem(true, "test archive item");

    @BeforeEach
    void init() {
        dataStorage = new InMemoryDataStorage();
    }

    @Test
    void testDefaultVersion() {
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
    }

    @Test
    void testInit() {
        Map<String, DataStorageTypes.Item> items = ImmutableMap.of(item1.getId(), item1, archivedItem.getId(), archivedItem);
        Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of(DATATESTS, items);
        dataStorage.init(allData, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
        assertNull(dataStorage.get(DATATESTS, archivedItem.getId()));
        assertEquals(1, dataStorage.getAll(DATATESTS).size());
    }

    @Test
    void testInvalidInit() {
        dataStorage.init(null, 1L);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of();
        dataStorage.init(allData, 1L);
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        Map<String, DataStorageTypes.Item> items = ImmutableMap.of(item1.getId(), item1);
        allData = ImmutableMap.of(DATATESTS, items);
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
        allData = ImmutableMap.of(DATATESTS, items);
        dataStorage.init(allData, 1L);
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(1, dataStorage.getAll(DATATESTS).size());
    }

    @Test
    void testGet() {
        Map<String, DataStorageTypes.Item> items = ImmutableMap.of(item1.getId(), item1, archivedItem.getId(), archivedItem);
        Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of(DATATESTS, items);
        dataStorage.init(allData, 1L);
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
        assertNull(dataStorage.get(DATATESTS, archivedItem.getId()));
        assertEquals(1, dataStorage.getAll(DATATESTS).size());
        assertEquals(item1, dataStorage.getAll(DATATESTS).get(item1.getId()));
    }

    @Test
    void testUpsert() {
        dataStorage.upsert(DATATESTS, item1.getId(), item1, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
        dataStorage.upsert(DATATESTS, item2.getId(), item2, 2L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(2L, dataStorage.getVersion());
        assertEquals(item2, dataStorage.get(DATATESTS, item2.getId()));
        item1 = new TestDataModel.TestItem(item1.getId(), false, "updated test item 1");
        dataStorage.upsert(DATATESTS, item1.getId(), item1, 3L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(3L, dataStorage.getVersion());
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
    }

    @Test
    void testInvalidUpsert() {
        assertFalse(dataStorage.upsert(DATATESTS, null, null, null));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        assertFalse(dataStorage.upsert(DATATESTS, item1.getId(), item1, null));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());
        assertFalse(dataStorage.upsert(DATATESTS, item1.getId(), item1, -1L));
        assertEquals(0L, dataStorage.getVersion());
        assertFalse(dataStorage.isInitialized());

        dataStorage.upsert(DATATESTS, item1.getId(), item1, 1L);
        assertTrue(dataStorage.isInitialized());
        assertEquals(1L, dataStorage.getVersion());
        assertFalse(dataStorage.upsert(DATATESTS, item2.getId(), item2, 1L));
        assertEquals(1L, dataStorage.getVersion());
    }

}
