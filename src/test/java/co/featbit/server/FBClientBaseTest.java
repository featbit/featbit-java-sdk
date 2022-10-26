package co.featbit.server;

import co.featbit.server.exterior.FBClient;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;

public abstract class FBClientBaseTest {

    protected static FBClient initClientInOfflineMode() throws IOException {
        FBConfig config = new FBConfig.Builder()
                .offline(true)
                .streamingURL("ws://fake-url")
                .eventURL("http://fake-url")
                .build();
        FBClient client = new FBClientImp("env-secret", config);
        client.initializeFromExternalJson(readResource("fbclient_test_data.json"));
        return client;
    }

    protected static String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }

    protected static class Dummy{
        int code;
        String reason;
    }
}
