package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.server.exterior.DefaultSender;
import co.featbit.server.exterior.InsightProcessor;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static co.featbit.server.InsightTypes.InsightMessageType.SHUTDOWN;

abstract class Insights {

    final static class InsightProcessorImpl implements InsightProcessor {

        private static final Duration AWAIT_TERMINATION = Duration.ofSeconds(2);

        private final BlockingQueue<InsightTypes.InsightMessage> inbox;

        private final ScheduledThreadPoolExecutor flushScheduledExecutor;

        private final AtomicBoolean closed = new AtomicBoolean(false);

        public InsightProcessorImpl(String eventUrl, DefaultSender sender, long flushInterval, int capacity) {
            this.inbox = new ArrayBlockingQueue<>(capacity);
            new EventDispatcher(Pair.of(eventUrl, sender), inbox);
            this.flushScheduledExecutor = new ScheduledThreadPoolExecutor(1, Utils.createThreadFactory("insight-periodic-flush-worker-%d", true));
            flushScheduledExecutor.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
            Loggers.EVENTS.debug("insight processor is ready");
        }

        @Override
        public void send(InsightTypes.Event event) {
            if (!closed.get() && event != null) {
                if (event instanceof InsightTypes.FlagEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.FLAGS, event);
                } else if (event instanceof InsightTypes.MetricEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.METRICS, event);
                } else {
                    Loggers.EVENTS.debug("ignore event type: {}", event.getClass().getName());
                }
            }
        }

        @Override
        public void flush() {
            if (!closed.get()) {
                putEventAsync(InsightTypes.InsightMessageType.FLUSH, null);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                Loggers.EVENTS.info("FFC JAVA SDK: insight processor is stopping");
                Utils.shutDownThreadPool("insight-periodic-flush-worker", flushScheduledExecutor, AWAIT_TERMINATION);
                //flush all the left events
                putEventAsync(InsightTypes.InsightMessageType.FLUSH, null);
                //shutdown, clear all the threads
                putEventAndWaitTermination(SHUTDOWN, null);
            }

        }

        private void putEventAsync(InsightTypes.InsightMessageType type, InsightTypes.Event event) {
            putMsgToInbox(new InsightTypes.InsightMessage(type, event, false));
        }

        private void putEventAndWaitTermination(InsightTypes.InsightMessageType type, InsightTypes.Event event) {
            InsightTypes.InsightMessage msg = new InsightTypes.InsightMessage(type, event, true);
            if (putMsgToInbox(msg)) {
                Loggers.EVENTS.debug("put {} WaitTermination message to inbox", type);
                msg.waitForComplete();
            }
        }

        private boolean putMsgToInbox(InsightTypes.InsightMessage msg) {
            if (inbox.offer(msg)) {
                return true;
            }
            if (msg.getType() == SHUTDOWN) {
                while (true) {
                    try {
                        // must put the shut down to inbox;
                        inbox.put(msg);
                        return true;
                    } catch (InterruptedException ignore) {
                    }
                }
            }
            // if it reaches here, it means the application is probably doing tons of flag evaluations across many threads.
            // So if we wait for a space in the inbox, we risk a very serious slowdown of the app.
            // To avoid that, we'll just drop the event or you can increase the capacity of inbox
            Loggers.EVENTS.warn("FFC JAVA SDK: events are being produced faster than they can be processed; some events will be dropped");
            return false;
        }

    }

    private final static class FlushPayload {
        private final InsightTypes.Event[] events;

        public FlushPayload(InsightTypes.Event[] events) {
            this.events = events;
        }

        public InsightTypes.Event[] getEvents() {
            return events;
        }
    }

    private final static class EventBuffer {
        private final List<InsightTypes.Event> incomingEvents = new ArrayList<>();

        void add(InsightTypes.Event event) {
            incomingEvents.add(event);
        }

        FlushPayload getPayload() {
            return new FlushPayload(incomingEvents.toArray(new InsightTypes.Event[0]));
        }

        void clear() {
            incomingEvents.clear();
        }

        boolean isEmpty() {
            return incomingEvents.isEmpty();
        }

    }

    private final static class FlushPayloadRunner {

        private final static int MAX_EVENT_SIZE_PER_REQUEST = 50;

        private final Pair<String, DefaultSender> config;

        private InsightTypes.Event[] payload;

        public FlushPayloadRunner(Pair<String, DefaultSender> config, InsightTypes.Event[] payload) {
            this.config = config;
            this.payload = payload;
        }

        public Boolean run() {
            try {
                // split the payload into small partitions and send them to featureflag.co
                Iterables.partition(Arrays.asList(payload), MAX_EVENT_SIZE_PER_REQUEST)
                        .forEach(partition -> {
                            String json = JsonHelper.serialize(partition);
                            config.getRight().postJson(config.getLeft(), json);
                            Loggers.EVENTS.debug("paload size: {}", partition.size());
                        });
            } catch (Exception unexpected) {
                Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in sending payload: {}", unexpected.getMessage());
                return false;
            }
            return true;
        }
    }

    private static final class EventDispatcher {
        private final static int MAX_FLUSH_WORKERS_NUMBER = 5;
        private final static int BATCH_SIZE = 50;
        private final static Duration AWAIT_TERMINATION = Duration.ofSeconds(2);
        private final BlockingQueue<InsightTypes.InsightMessage> inbox;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private final Object lockObject = new Object();
        private final Pair<String, DefaultSender> config;
        private final List<InsightTypes.Event> nextFlushBuffer = new ArrayList<>();
        // permits to flush events
        private final ThreadPoolExecutor flushWorkers;

        private final Semaphore permits = new Semaphore(MAX_FLUSH_WORKERS_NUMBER);

        public EventDispatcher(Pair<String, DefaultSender> config, BlockingQueue<InsightTypes.InsightMessage> inbox) {
            this.config = config;
            this.inbox = inbox;
            this.flushWorkers = new ThreadPoolExecutor(MAX_FLUSH_WORKERS_NUMBER,
                    MAX_FLUSH_WORKERS_NUMBER,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(MAX_FLUSH_WORKERS_NUMBER),
                    Utils.createThreadFactory("flush-payload-worker-%d", true),
                    new ThreadPoolExecutor.CallerRunsPolicy());

            Thread mainThread = Utils.createThreadFactory("event-dispatcher", true).newThread(this::dispatchEvents);
            mainThread.start();
        }

        // blocks until a message is available and then:
        // 1: transfer the events to event buffer
        // 2: try to flush events to featureflag if a flush message arrives
        // 3: wait for releasing resources if a shutdown arrives
        private void dispatchEvents() {
            List<InsightTypes.InsightMessage> messages = new ArrayList<>();
            Loggers.EVENTS.debug("event dispatcher is working...");
            while (true) {
                try {
                    messages.clear();
                    messages.add(inbox.take());
                    inbox.drainTo(messages, BATCH_SIZE - 1);  // this nonblocking call allows us to pick up more messages if available
                    for (InsightTypes.InsightMessage message : messages) {
                        try {
                            switch (message.getType()) {
                                case FLAGS:
                                case METRICS:
                                    putEventToNextBuffer(message.getEvent());
                                    break;
                                case FLUSH:
                                    triggerFlush();
                                    break;
                                case SHUTDOWN:
                                    shutdown();
                                    message.completed();
                                    return;
                            }
                            message.completed();
                        } catch (Exception unexpected) {
                            Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in event dispatcher {}", unexpected.getMessage());
                        }
                    }
                } catch (InterruptedException ignore) {
                } catch (Exception unexpected) {
                    Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in event dispatcher {}", unexpected.getMessage());
                }
            }
        }

        private void waitUntilFlushPayLoadWorkerDown() {
            synchronized (lockObject) {
                while (permits.availablePermits() < MAX_FLUSH_WORKERS_NUMBER) {
                    try {
                        lockObject.wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }

        private void putEventToNextBuffer(InsightTypes.Event event) {
            if (closed.get()) {
                return;
            }
            if (event.isSendEvent()) {
                Loggers.EVENTS.debug("put event to buffer");
                nextFlushBuffer.add(event);
            }

        }

        private void triggerFlush() {
            if (closed.get() || nextFlushBuffer.isEmpty()) {
                return;
            }

            //get all the current events from event buffer
            InsightTypes.Event[] payload = nextFlushBuffer.toArray(new InsightTypes.Event[0]);
            if (permits.tryAcquire()) {
                Loggers.EVENTS.debug("trigger flush");
                // get an available flush worker to send events
                CompletableFuture
                        .supplyAsync(() -> new FlushPayloadRunner(config, payload).run(), flushWorkers)
                        .whenComplete((res, exception) -> {
                            permits.release();
                            synchronized (lockObject) {
                                lockObject.notifyAll();
                            }
                        });
                // clear unused buffer for next flush
                nextFlushBuffer.clear();
            }
            // if no more available flush workers , the buffer will be merged in the next flush
        }

        private void shutdown() {
            Loggers.EVENTS.debug("event dispatcher clean up threads and conn pool");
            try {
                // wait util all payloads are well sent
                waitUntilFlushPayLoadWorkerDown();
                // shutdown resources
                if (closed.compareAndSet(false, true)) {
                    Utils.shutDownThreadPool("flush-payload-worker", flushWorkers, AWAIT_TERMINATION);
                    config.getRight().close();
                }
            } catch (Exception unexpected) {
                Loggers.EVENTS.error("FFC JAVA SDK: unexpected error when closing event dispatcher: {}", unexpected.getMessage());
            }
        }

    }

}
