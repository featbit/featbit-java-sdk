package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FlagTracker;
import co.featbit.server.exterior.FlagValueChangeEvent;
import co.featbit.server.exterior.FlagValueChangeListener;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

class FlagTrackerImpl implements FlagTracker {

    private final EventBroadcaster<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier;

    private final BiFunction<String, FBUser, Object> evaluateFn;

    FlagTrackerImpl(EventBroadcaster<FlagChange.FlagChangeListener, FlagChange.FlagChangeEvent> flagChangeEventNotifier,
                    BiFunction<String, FBUser, Object> evaluateFn) {
        this.flagChangeEventNotifier = flagChangeEventNotifier;
        this.evaluateFn = evaluateFn;
    }

    @Override
    public FlagChange.FlagChangeListener addFlagValueChangeListener(String flagKey, FBUser user, FlagValueChangeListener listener) {
        FlagChange.FlagChangeListener adapter = new FlagValueChangeAdapter(flagKey, user, listener);
        addFlagChangeListener(adapter);
        return adapter;
    }

    @Override
    public void removeFlagChangeListener(FlagChange.FlagChangeListener listener) {
        flagChangeEventNotifier.removeListener(listener);
    }

    @Override
    public void addFlagChangeListener(FlagChange.FlagChangeListener listener) {
        flagChangeEventNotifier.addListener(listener);
    }

    private final class FlagValueChangeAdapter implements FlagChange.FlagChangeListener {
        private final String flagKey;
        private final FBUser user;
        private final FlagValueChangeListener listener;
        private final AtomicReference<Object> value;

        FlagValueChangeAdapter(String flagKey, FBUser user, FlagValueChangeListener listener) {
            this.flagKey = flagKey;
            this.user = user;
            this.listener = listener;
            this.value = new AtomicReference<>(evaluateFn.apply(flagKey, user));
        }

        @Override
        public void onFlagChange(FlagChange.FlagChangeEvent event) {
            if (event.getKey().equals(flagKey)) {
                Object newValue = evaluateFn.apply(flagKey, user);
                Object oldValue = value.getAndSet(newValue);
                if (newValue != null && !newValue.equals(oldValue)) {
                    listener.onFlagValueChange(new FlagValueChangeEvent(flagKey, oldValue, newValue));
                }
            }
        }

    }
}
