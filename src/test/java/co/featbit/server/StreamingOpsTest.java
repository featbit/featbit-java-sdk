package co.featbit.server;

import co.featbit.commons.json.JsonParseException;
import co.featbit.server.exterior.DataStorage;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.featbit.server.Status.DATA_INVALID_ERROR;
import static co.featbit.server.Status.DATA_STORAGE_INIT_ERROR;
import static co.featbit.server.Status.NETWORK_ERROR;
import static co.featbit.server.Status.RUNTIME_ERROR;
import static co.featbit.server.Status.StateType.INITIALIZING;
import static co.featbit.server.Status.StateType.INTERRUPTED;
import static co.featbit.server.Status.StateType.OFF;
import static co.featbit.server.Status.StateType.OK;
import static co.featbit.server.Status.UNKNOWN_ERROR;
import static co.featbit.server.Status.WEBSOCKET_ERROR;
import static co.featbit.server.Streaming.StreamingOps.isReconnOnClose;
import static co.featbit.server.Streaming.StreamingOps.isReconnOnFailure;
import static co.featbit.server.Streaming.StreamingOps.processData;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * this class just tests streaming operations, not covers the connection test
 */
class StreamingOpsTest extends ComponentBaseTest {
    private DataStorage dataStorage;

    private Status.DataUpdaterImpl dataUpdaterImpl;

    private final EasyMockSupport support = new EasyMockSupport();

    private Status.DataUpdater dataUpdaterMock;

    @BeforeEach
    void init() {
        dataStorage = new InMemoryDataStorage();
        dataUpdaterImpl = new Status.DataUpdaterImpl(dataStorage);
        dataUpdaterMock = support.createNiceMock(Status.DataUpdater.class);
    }

    @Test
    void testProcessFullData() throws Exception {
        AtomicBoolean initialized = new AtomicBoolean(false);
        CompletableFuture<Boolean> initFuture = new CompletableFuture<>();

        DataModel.Data data = loadData();
        assertTrue(processData(dataUpdaterImpl, data, initialized, initFuture));
        assertTrue(initialized.get());
        assertTrue(initFuture.get());
        assertTrue(dataUpdaterImpl.storageInitialized());
        assertEquals(OK, dataUpdaterImpl.getCurrentState().getStateType());
        assertEquals(data.getTimestamp(), dataUpdaterImpl.getVersion());
    }

    @Test
    void testProcessPatchData() throws Exception {
        AtomicBoolean initialized = new AtomicBoolean(false);
        CompletableFuture<Boolean> initFuture = new CompletableFuture<>();

        DataModel.Data data = loadData();
        data.eventType = "patch";
        assertTrue(processData(dataUpdaterImpl, data, initialized, initFuture));
        assertTrue(initialized.get());
        assertTrue(initFuture.get());
        assertTrue(dataUpdaterImpl.storageInitialized());
        assertEquals(OK, dataUpdaterImpl.getCurrentState().getStateType());
        assertEquals(data.getTimestamp(), dataUpdaterImpl.getVersion());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessDataThrowException() throws Exception {
        dataStorage = support.createNiceMock(DataStorage.class);
        dataUpdaterImpl = new Status.DataUpdaterImpl(dataStorage);

        AtomicBoolean initialized = new AtomicBoolean(false);
        CompletableFuture<Boolean> initFuture = new CompletableFuture<>();

        dataStorage.init(anyObject(Map.class), anyLong());
        expectLastCall().andThrow(new RuntimeException("test exception"));
        expect(dataStorage.isInitialized()).andReturn(false).anyTimes();
        support.replayAll();

        DataModel.Data data = loadData();
        assertFalse(processData(dataUpdaterImpl, data, initialized, initFuture));
        assertFalse(initialized.get());
        assertFalse(dataUpdaterImpl.storageInitialized());
        assertEquals(INITIALIZING, dataUpdaterImpl.getCurrentState().getStateType());
        assertEquals(DATA_STORAGE_INIT_ERROR, dataUpdaterImpl.getCurrentState().getErrorTrack().getErrorType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnNormalClose() {
        int code = 1000;
        String reason = "normal close";
        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertFalse(isReconnOnClose(dataUpdaterMock, code, reason));
        assertEquals(OFF, ((Status.State) arguments.get(0)).getStateType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnInvalidRequest() {
        int code = 4003;
        String reason = "invalid request";
        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertFalse(isReconnOnClose(dataUpdaterMock, code, reason));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(OFF, state.getStateType());
        assertEquals(reason, state.getErrorTrack().getMessage());
        support.verifyAll();
    }

    @Test
    void testStreamingOnDataSyncError() {
        int code = 1001;
        String reason = "data sync error";
        assertTrue(isReconnOnClose(dataUpdaterMock, code, reason));
    }

    @Test
    void testStreamingOnUnknownCode() {
        int code = 1013;
        String reason = "try again later";
        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertTrue(isReconnOnClose(dataUpdaterMock, code, reason));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(INTERRUPTED, state.getStateType());
        assertEquals(reason, state.getErrorTrack().getMessage());
        support.verifyAll();
    }

    @Test
    void testStreamingOnJsonParseException() {
        Exception ex = new JsonParseException("test exception");

        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertFalse(isReconnOnFailure(dataUpdaterMock, ex));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(OFF, state.getStateType());
        assertEquals(DATA_INVALID_ERROR, state.getErrorTrack().getErrorType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnRuntimeException() {
        Exception ex = new RuntimeException("test exception");

        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertTrue(isReconnOnFailure(dataUpdaterMock, ex));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(INTERRUPTED, state.getStateType());
        assertEquals(RUNTIME_ERROR, state.getErrorTrack().getErrorType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnWebSocketError() {
        Exception ex = new SocketTimeoutException("test exception");

        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertTrue(isReconnOnFailure(dataUpdaterMock, ex));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(INTERRUPTED, state.getStateType());
        assertEquals(WEBSOCKET_ERROR, state.getErrorTrack().getErrorType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnNetworkError() {
        Exception ex = new IOException("test exception");

        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertTrue(isReconnOnFailure(dataUpdaterMock, ex));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(INTERRUPTED, state.getStateType());
        assertEquals(NETWORK_ERROR, state.getErrorTrack().getErrorType());
        support.verifyAll();
    }

    @Test
    void testStreamingOnUnknownException() {
        Exception ex = new Exception("test exception");

        final List<Object> arguments = new ArrayList<>();
        dataUpdaterMock.updateStatus(anyObject(Status.State.class));
        expectLastCall().andAnswer(() -> {
            arguments.addAll(Arrays.asList(getCurrentArguments()));
            return null;
        });
        support.replayAll();
        assertTrue(isReconnOnFailure(dataUpdaterMock, ex));
        Status.State state = (Status.State) (arguments.get(0));
        assertEquals(INTERRUPTED, state.getStateType());
        assertEquals(UNKNOWN_ERROR, state.getErrorTrack().getErrorType());
        support.verifyAll();
    }


}
