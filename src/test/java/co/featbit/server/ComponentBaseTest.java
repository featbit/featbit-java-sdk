package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.server.exterior.FlagValueChangeEvent;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class ComponentBaseTest {

    protected static ExecutorService sharedExcutor = new ScheduledThreadPoolExecutor(1, Utils.createThreadFactory("featbit-test-shared-worker-%d", true));

    protected DataModel.Data loadData() throws Exception {
        DataModel.All all = JsonHelper.deserialize(Resources.toString(Resources.getResource("fbclient_test_data.json"), Charsets.UTF_8), DataModel.All.class);
        return (all.isProcessData()) ? all.data() : null;
    }

    protected static <T extends FlagChange.FlagChangeEvent> void expectFlagChangeEvents(BlockingQueue<T> events, String... flagKeys) throws Exception {
        Set<String> expectedChangedFlagKeys = ImmutableSet.copyOf(flagKeys);
        Set<String> actualChangedFlagKeys = new HashSet<>();
        for (int i = 0; i < expectedChangedFlagKeys.size(); i++) {
            T e = events.poll(1, TimeUnit.SECONDS);
            actualChangedFlagKeys.add(e.getKey());
        }
        assertEquals(expectedChangedFlagKeys, actualChangedFlagKeys);
    }

    protected static <S extends Status.State> void expectStateEvents(BlockingQueue<S> events, Status.StateType... stateTypes) throws Exception {
        Set<Status.StateType> expectedStateTypes = ImmutableSet.copyOf(stateTypes);
        Set<Status.StateType> actualStateTypes = new HashSet<>();
        for (int i = 0; i < expectedStateTypes.size(); i++) {
            S e = events.poll(1, TimeUnit.SECONDS);
            actualStateTypes.add(e.getStateType());
        }
        assertEquals(expectedStateTypes, actualStateTypes);
    }

    protected static <T extends FlagValueChangeEvent> void expectFlagValueChangeEvents(BlockingQueue<T> queue, FlagValueChangeEvent... events) throws Exception {
        Set<FlagValueChangeEvent> expectedEvents = ImmutableSet.copyOf(events);
        Set<FlagValueChangeEvent> actualEvents = new HashSet<>();
        for (int i = 0; i < expectedEvents.size(); i++) {
            T e = queue.poll(1, TimeUnit.SECONDS);
            actualEvents.add(e);
        }
        assertEquals(expectedEvents, expectedEvents);
    }

}
