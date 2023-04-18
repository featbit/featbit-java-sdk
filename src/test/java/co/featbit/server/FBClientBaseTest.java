package co.featbit.server;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

public abstract class FBClientBaseTest {

    protected String fakeUrl = "http://fake";

    protected String fakeEnvSecret = Base64.getUrlEncoder().encodeToString(fakeUrl.getBytes());

    protected FBClientImp initClientInOfflineMode() throws IOException {
        FBConfig config = new FBConfig.Builder()
                .offline(true)
                .streamingURL("ws://fake-url")
                .eventURL("http://fake-url")
                .build();
        FBClientImp client = new FBClientImp("env-secret", config);
        client.initializeFromExternalJson(readResource("fbclient_test_data.json"));
        return client;
    }

    protected FBClientImp initClientWithNullComponents(Duration duration) {
        FBConfig config = new FBConfig.Builder()
                .startWaitTime(duration)
                .streamingURL("ws://fake-url")
                .eventURL("http://fake-url")
                .dataSynchronizerFactory(Factory.externalDataSynchronization())
                .insightProcessorFactory(Factory.externalEventTrack())
                .build();
        FBClientImp client = new FBClientImp("env-secret", config);
        return client;
    }

    protected String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }

    protected static class Dummy {
        int code;
        String reason;
    }
}
