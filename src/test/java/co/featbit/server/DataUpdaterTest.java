package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageTypes;
import com.google.common.collect.ImmutableMap;
import org.easymock.EasyMockExtension;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;

import static co.featbit.server.Status.DATA_STORAGE_INIT_ERROR;
import static co.featbit.server.Status.DATA_STORAGE_UPDATE_ERROR;
import static co.featbit.server.Status.StateType.INITIALIZING;
import static co.featbit.server.Status.StateType.INTERRUPTED;
import static co.featbit.server.Status.StateType.OFF;
import static co.featbit.server.exterior.DataStorageTypes.DATATESTS;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EasyMockExtension.class)
class DataUpdaterTest {
    private DataStorage dataStorage;
    private Status.DataUpdaterImpl dataUpdater;
    private Status.DataUpdateStatusProvider dataUpdateStatusProvider;
    private final EasyMockSupport support = new EasyMockSupport();
    private final DataStorageTypes.Item item1 = new TestDataModel.TestItem(false, "test item 1");

    @AfterEach
    void dispose() {
        dataStorage = null;
        dataUpdater = null;
        dataUpdateStatusProvider = null;
    }

    @Test
    void testInitDataStorage() {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);

        Map<String, DataStorageTypes.Item> items = ImmutableMap.of(item1.getId(), item1);
        Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of(DATATESTS, items);
        dataUpdater.init(allData, 1L);
        assertTrue(dataUpdater.storageInitialized());
        assertEquals(1L, dataUpdater.getVersion());
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
    }

    @Test
    void testInitDataStorageThrowException() {
        dataStorage = support.createNiceMock(DataStorage.class);
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);

        dataStorage.init(anyObject(Map.class), anyLong());
        expectLastCall().andThrow(new RuntimeException("test exception"));
        expect(dataStorage.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();

        Map<String, DataStorageTypes.Item> items = ImmutableMap.of(item1.getId(), item1);
        Map<DataStorageTypes.Category, Map<String, DataStorageTypes.Item>> allData = ImmutableMap.of(DATATESTS, items);
        dataUpdater.init(allData, 1L);
        assertEquals(INITIALIZING, dataUpdater.getCurrentState().getStateType());
        assertEquals(DATA_STORAGE_INIT_ERROR, dataUpdater.getCurrentState().getErrorTrack().getErrorType());
        assertFalse(dataUpdater.storageInitialized());
    }

    @Test
    void testUpsertDataStorage() {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);

        assertTrue(dataUpdater.upsert(DATATESTS, item1.getId(), item1, 1L));
        assertTrue(dataUpdater.storageInitialized());
        assertEquals(1L, dataUpdater.getVersion());
        assertEquals(item1, dataStorage.get(DATATESTS, item1.getId()));
    }

    @Test
    void testUpsertDataStorageThrowException() {
        dataStorage = support.createNiceMock(DataStorage.class);
        dataUpdater = new Status.DataUpdaterImpl(dataStorage, Status.State.OKState());

        expect(dataStorage.upsert(anyObject(DataStorageTypes.Category.class),
                anyString(),
                anyObject(DataStorageTypes.Item.class),
                anyLong())).andThrow(new RuntimeException("test exception"));
        expect(dataStorage.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();
        dataUpdater.upsert(DATATESTS, item1.getId(), item1, 1L);
        assertEquals(INTERRUPTED, dataUpdater.getCurrentState().getStateType());
        assertEquals(DATA_STORAGE_UPDATE_ERROR, dataUpdater.getCurrentState().getErrorTrack().getErrorType());
        assertFalse(dataUpdater.storageInitialized());
    }

    @Test
    void testUpdateStatus() {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);
        dataUpdater.updateStatus(Status.State.interruptedState("some type", "some reason"));
        assertEquals(INITIALIZING, dataUpdater.getCurrentState().getStateType());
        dataUpdater = null;
        dataUpdater = new Status.DataUpdaterImpl(dataStorage, Status.State.OKState());
        dataUpdater.updateStatus(Status.State.interruptedState("some type", "some reason"));
        assertEquals(INTERRUPTED, dataUpdater.getCurrentState().getStateType());
    }

    @Test
    void waitForStatusIfStatusAlreadyCorrect() throws InterruptedException {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);
        dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdater);

        dataUpdater.updateStatus(Status.State.OKState());
        assertTrue(dataUpdateStatusProvider.waitForOKState(Duration.ofMillis(100)));

        dataUpdater.updateStatus(Status.State.normalOFFState());
        assertTrue(dataUpdateStatusProvider.waitFor(OFF, Duration.ofMillis(100)));
    }

    @Test
    void waitForStatusWhenStatusSucceeded() throws InterruptedException {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);
        dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdater);

        new Thread(() -> {
            try {
                Thread.sleep(50);
                dataUpdater.updateStatus(Status.State.OKState());
            } catch (InterruptedException ignored) {
            }
        }).start();
        long timeStart = System.currentTimeMillis();
        assertTrue(dataUpdateStatusProvider.waitForOKState(Duration.ofMillis(100)));
        long timeEnd = System.currentTimeMillis();
        assertTrue(timeEnd - timeStart >= 50);
        assertTrue(timeEnd - timeStart < 100);
    }

    @Test
    void waitForStatusTimeOut() throws InterruptedException {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);
        dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdater);

        long timeStart = System.currentTimeMillis();
        assertFalse(dataUpdateStatusProvider.waitForOKState(Duration.ofMillis(10)));
        long timeEnd = System.currentTimeMillis();
        assertTrue(timeEnd - timeStart >= 10);
    }

    @Test
    void waitForStatusButStatusOff() throws InterruptedException {
        dataStorage = new InMemoryDataStorage();
        dataUpdater = new Status.DataUpdaterImpl(dataStorage);
        dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdater);

        new Thread(() -> {
            try {
                Thread.sleep(50);
                dataUpdater.updateStatus(Status.State.normalOFFState());
            } catch (InterruptedException ignored) {
            }
        }).start();

        long timeStart = System.currentTimeMillis();
        assertFalse(dataUpdateStatusProvider.waitForOKState(Duration.ofMillis(100)));
        long timeEnd = System.currentTimeMillis();
        assertTrue(timeEnd - timeStart >= 50);
        assertTrue(timeEnd - timeStart < 100);
    }

}
