package co.featbit.server;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.InsightProcessor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightProcessorTest {
    private static final String FAKE_URL = "http://fakeurl";

    private final FBUser user1 = new FBUser.Builder("test-user-1").userName("test-user-1").build();

    private final FBUser user2 = new FBUser.Builder("test-user-2").userName("test-user-2").build();

    private final FBUser user3 = new FBUser.Builder("test-user-3").userName("test-user-3").build();

    @Test
    void testInsightProcessorStartAndClose() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            assertNull(sender.getLastSendingJsonInfo(200));
        }
        assertTrue(sender.closed.get());
    }

    @Test
    void testInsightProcessorCanGracefullyCloseIfSenderErrorOnClose() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            sender.fakeErrorOnClose = new IOException("test Exception");
            assertNull(sender.getLastSendingJsonInfo(200));
        }
        assertTrue(sender.closed.get());
    }

    @Test
    void testInsightProcessorSendEventsAndAutoFlush() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            insightProcessor.send(InsightTypes.UserEvent.of(user1));
            insightProcessor.send(InsightTypes.UserEvent.of(user2));
            TestSenders.SendingJsonInfo res = sender.getLastSendingJsonInfo(200);
            if (res.payloadSize == 1) {
                assertTrue(res.isContainsUser(user1.getKey()));
            } else {
                assertTrue(res.isContainsUser(user1.getKey()));
                assertTrue(res.isContainsUser(user2.getKey()));
            }
        }
        assertTrue(sender.closed.get());
    }

    @Test
    void testInsightProcessorSendEventsAndManuelFlush() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            insightProcessor.send(InsightTypes.UserEvent.of(user1));
            insightProcessor.flush();
            TestSenders.SendingJsonInfo res = sender.getLastSendingJsonInfo(200);
            assertEquals(1, res.payloadSize);
            assertTrue(res.isContainsUser(user1.getKey()));
            insightProcessor.send(InsightTypes.UserEvent.of(user2));
            insightProcessor.flush();
            res = sender.getLastSendingJsonInfo(200);
            assertEquals(1, res.payloadSize);
            assertTrue(res.isContainsUser(user2.getKey()));
        }
        assertTrue(sender.closed.get());
    }

    @Test
    void testInsightProcessorCanWorkEvenIfSenderError() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            sender.fakeError = new RuntimeException("test exception");
            insightProcessor.send(InsightTypes.UserEvent.of(user1));
            insightProcessor.flush();
            sender.getLastSendingJsonInfo(200);

            sender.fakeError = null;
            insightProcessor.send(InsightTypes.UserEvent.of(user2));
            insightProcessor.flush();
            TestSenders.SendingJsonInfo res = sender.getLastSendingJsonInfo(200);
            assertEquals(1, res.payloadSize);
            assertTrue(res.isContainsUser(user2.getKey()));
        }
        assertTrue(sender.closed.get());
    }

    @Test
    void testInsightProcessorCanNotSendAnythingAfterClose() throws IOException {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100);
        assertNull(sender.getLastSendingJsonInfo(200));
        insightProcessor.close();
        assertTrue(sender.closed.get());
        insightProcessor.send(InsightTypes.UserEvent.of(user1));
        insightProcessor.flush();
        assertNull(sender.getLastSendingJsonInfo(200));
    }

    @Test
    void testInsightProcessorEventsKeepInBufferIfAllFlushWorkerAreBusy() throws Exception {
        TestSenders.MockInsightSender sender = new TestSenders.MockInsightSender();
        try (InsightProcessor insightProcessor = new Insights.InsightProcessorImpl(FAKE_URL, sender, 100, 100)) {
            Object waitObject = new Object();
            CountDownLatch threadCounter = new CountDownLatch(5);
            sender.waitObject = waitObject;
            sender.threadCounter = threadCounter;
            for (int i = 0; i < 5; i++){
                insightProcessor.send(InsightTypes.UserEvent.of(user1));
                insightProcessor.flush();
                sender.getLastSendingJsonInfo(200);
            }
            threadCounter.await();
            sender.waitObject = null;
            sender.threadCounter = null;
            insightProcessor.send(InsightTypes.UserEvent.of(user2));
            insightProcessor.flush();
            insightProcessor.send(InsightTypes.UserEvent.of(user3));
            insightProcessor.flush();
            synchronized (waitObject){
                waitObject.notifyAll();
            }
            TestSenders.SendingJsonInfo res = sender.getLastSendingJsonInfo(200);
            assertEquals(2, res.payloadSize);
            assertTrue(res.isContainsUser(user2.getKey()));
            assertTrue(res.isContainsUser(user3.getKey()));
        }
        assertTrue(sender.closed.get());
    }


}
