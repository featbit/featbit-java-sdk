package co.featbit.server;

import co.featbit.commons.json.JsonHelper;
import co.featbit.server.exterior.DefaultSender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

abstract class TestSenders {
    static final class SendingJsonInfo {
        int payloadSize;
        JsonArray payload;

        public SendingJsonInfo(JsonArray payload) {
            this.payloadSize = payload.size();
            this.payload = payload;
        }

        boolean isContainsUser(String userKeyId) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(payload.iterator(), Spliterator.ORDERED), false)
                    .map(element -> element.getAsJsonObject().get("user"))
                    .anyMatch(element -> element.getAsJsonObject().get("keyId").getAsString().equals(userKeyId));
        }
    }

    static final class MockInsightSender implements DefaultSender {
        // volatile make sure that changes made by one thread to the shared data are visible
        // to other threads to maintain data consistency.

        volatile AtomicBoolean closed = new AtomicBoolean(false);

        volatile BlockingQueue<SendingJsonInfo> buffer = new ArrayBlockingQueue<>(100);

        volatile CountDownLatch threadCounter;

        volatile Object waitObject;

        volatile RuntimeException fakeError;

        volatile IOException fakeErrorOnClose;

        MockInsightSender() {
        }

        @Override
        public String postJson(String url, String jsonBody) {
            JsonElement element = JsonHelper.deserialize(jsonBody, JsonElement.class);
            buffer.offer(new SendingJsonInfo(element.getAsJsonArray()));
            if (waitObject != null) {
                synchronized (waitObject) {
                    if (threadCounter != null) {
                        threadCounter.countDown();
                    }
                    try {
                        waitObject.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            if (fakeError != null) {
                throw fakeError;
            }
            Loggers.EVENTS.debug("sending events ok");
            return null;
        }

        SendingJsonInfo getLastSendingJsonInfo(int timeOutInMillis) {
            try {
                return buffer.poll(timeOutInMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            Loggers.EVENTS.debug("close mock sender");
            closed.set(true);
            if (fakeErrorOnClose != null) {
                throw fakeErrorOnClose;
            }
        }
    }

}
