package co.featbit.example;

import co.featbit.server.FBClientImp;
import co.featbit.server.FBConfig;
import co.featbit.server.exterior.FBClient;
import co.featbit.server.integrations.FeatGateProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean(destroyMethod = "close")
    FBClient fbClient(@Value("${featbit.offline}") boolean offline,
                      @Value("${featbit.env-secret}") String envSecret,
                      @Value("${featbit.streaming-url}") String streamingUrl,
                      @Value("${featbit.event-url}") String eventUrl) throws IOException {
        String flags = offline ? new ClassPathResource("flags.json").getContentAsString(StandardCharsets.UTF_8) : null;
        FBConfig config = new FBConfig.Builder()
                .offline(offline)
                .streamingURL(streamingUrl)
                .eventURL(eventUrl)
                .build();
        FBClient client = new FBClientImp(envSecret, config);
        if (offline && !client.initializeFromExternalJson(flags)) {
            client.close();
            throw new IllegalStateException("Could not load example flags");
        }
        return client;
    }

    @Bean
    UserService userService(FBClient client) {
        return FeatGateProxy.create(UserService.class, new DefaultUserService(), client);
    }
}
