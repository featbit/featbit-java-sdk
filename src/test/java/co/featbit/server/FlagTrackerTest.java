package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FlagValueChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagTrackerTest extends ComponentBaseTest {
    @Test
    void addflagChangeListeners() throws Exception {
        String flagKey = "test-flag";
        EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier = EventBroadcasterImpl.forFlagChangeEvents(ComponentBaseTest.sharedExcutor, Loggers.TEST);
        FlagTrackerImpl flagTracker = new FlagTrackerImpl(flagChangeEventNotifier, null);

        BlockingQueue<FlagChange.FlagChangeEvent> events1 = new LinkedBlockingQueue<>();
        FlagChange.FlagChangeListener listener1 = events1::add;
        flagTracker.addFlagChangeListener(listener1);

        BlockingQueue<FlagChange.FlagChangeEvent> events2 = new LinkedBlockingQueue<>();
        FlagChange.FlagChangeListener listener2 = events2::add;
        flagTracker.addFlagChangeListener(listener2);

        flagChangeEventNotifier.broadcast(new FlagChange.FlagChangeEvent(flagKey));

        expectFlagChangeEvents(events1, flagKey);
        expectFlagChangeEvents(events2, flagKey);

        flagTracker.removeFlagChangeListener(listener1);
    }

    @Test
    void addFlagValueChangeListeners() throws Exception {
        String flagKey = "test-flag";
        FBUser user1 = new FBUser.Builder("test-user-1").userName("test-user-1").build();
        FBUser user2 = new FBUser.Builder("test-user-2").userName("test-user-2").build();

        Map<Map.Entry<String, FBUser>, Boolean> resultMap = new HashMap<>();
        EventBroadcasterImpl<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier = EventBroadcasterImpl.forFlagChangeEvents(ComponentBaseTest.sharedExcutor, Loggers.TEST);
        FlagTrackerImpl flagTracker = new FlagTrackerImpl(flagChangeEventNotifier, (k, u)->resultMap.get(new AbstractMap.SimpleEntry<>(k, u)));

        resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, user1), false);
        resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, user2), false);

        BlockingQueue<FlagValueChangeEvent> events1 = new LinkedBlockingQueue<>();
        BlockingQueue<FlagValueChangeEvent> events2 = new LinkedBlockingQueue<>();
        FlagChange.FlagChangeListener listener1 = flagTracker.addFlagValueChangeListener(flagKey, user1, events1::add);
        FlagChange.FlagChangeListener listener2 = flagTracker.addFlagValueChangeListener(flagKey, user2, events2::add);

        resultMap.put(new AbstractMap.SimpleEntry<>(flagKey, user1), true);
        flagChangeEventNotifier.broadcast(new FlagChange.FlagChangeEvent(flagKey));

        expectFlagValueChangeEvents(events1, new FlagValueChangeEvent(flagKey, false, true));
        assertTrue(events2.isEmpty());

        flagTracker.removeFlagChangeListener(listener1);
        flagTracker.removeFlagChangeListener(listener2);
    }
}
