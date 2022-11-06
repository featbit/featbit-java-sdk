package co.featbit.server;

import co.featbit.server.exterior.HttpConfig;
import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static co.featbit.server.Evaluator.FLAG_BOOL_TYPE;
import static co.featbit.server.Evaluator.FLAG_JSON_TYPE;
import static co.featbit.server.Evaluator.FLAG_NUMERIC_TYPE;
import static co.featbit.server.Evaluator.FLAG_STRING_TYPE;

public abstract class Utils {

    public static Iterable<Map.Entry<String, String>> defaultHeaders(String envSecret) {
        return ImmutableMap.of("Authorization", envSecret,
                        "User-Agent", "fb-java-server-sdk",
                        "Content-Type", "application/json")
                .entrySet();
    }

    public static ThreadFactory createThreadFactory(final String nameStyle,
                                                    final boolean isDaemon) {
        return new BasicThreadFactory.Builder()
                .namingPattern(nameStyle)
                .daemon(isDaemon)
                .build();
    }

    public static Proxy buildHTTPProxy(String proxyHost, int proxyPort) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * See <a href="https://stackoverflow.com/questions/35554380/okhttpclient-proxy-authentication-how-to">proxy authentication</a>
     *
     * @param username username
     * @param password password
     * @return {@link Authenticator}
     */
    @Beta
    public static Authenticator buildAuthenticator(String username, String password) {
        return (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response
                    .request()
                    .newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
    }

    public static Headers.Builder headersBuilderFor(HttpConfig config) {
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> kv : config.headers()) {
            builder.add(kv.getKey(), kv.getValue());
        }
        return builder;
    }

    public static void buildProxyAndSocketFactoryFor(OkHttpClient.Builder builder, HttpConfig httpConfig) {
        if (httpConfig.socketFactory() != null) {
            builder.socketFactory(httpConfig.socketFactory());
        }

        if (httpConfig.sslSocketFactory() != null) {
            builder.sslSocketFactory(httpConfig.sslSocketFactory(), httpConfig.trustManager());
        }

        if (httpConfig.proxy() != null) {
            builder.proxy(httpConfig.proxy());
            if (httpConfig.authenticator() != null) {
                builder.proxyAuthenticator(httpConfig.authenticator());
            }
        }
    }

    private static final Map<String, String> ALPHABETS;

    static {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        ALPHABETS = builder.put("0", "Q")
                .put("1", "B")
                .put("2", "W")
                .put("3", "S")
                .put("4", "P")
                .put("5", "H")
                .put("6", "D")
                .put("7", "X")
                .put("8", "Z")
                .put("9", "U")
                .build();
    }

    private static String encodeNumber(long number, int length) {
        String str = "000000000000" + number;
        String numberWithLeadingZeros = str.substring(str.length() - length);
        return new ArrayList<>(Arrays.asList(numberWithLeadingZeros.split("")))
                .stream().map(ALPHABETS::get).collect(Collectors.joining());

    }

    public static String buildToken(String envSecret) {
        String text = StringUtils.stripEnd(envSecret, "=");
        long now = Instant.now().toEpochMilli();
        String timestampCode = encodeNumber(now, String.valueOf(now).length());
        int start = Math.max((int) Math.floor(Math.random() * text.length()), 2);
        String part1 = encodeNumber(start, 3);
        String part2 = encodeNumber(timestampCode.length(), 2);
        String part3 = text.substring(0, start);
        String part5 = text.substring(start);
        return String.format("%s%s%s%s%s", part1, part2, part3, timestampCode, part5);
    }

    // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/#shutdown-isnt-necessary
    public static void shutdownOKHttpClient(String name, OkHttpClient client) {
        Loggers.UTILS.trace("Shutdown the dispatcher’s executor service");
        client.dispatcher().executorService().shutdown();
        Loggers.UTILS.trace("Clear the connection pool");
        client.connectionPool().evictAll();
        try (Cache ignored = client.cache()) {
            Loggers.UTILS.trace("If your client has a cache, call close()");
        } catch (Exception ignore) {
        }
        Loggers.UTILS.debug("gracefully clean up okhttpclient in {}", name);
    }

    // https://ld246.com/article/1488023925829
    public static void shutDownThreadPool(String name, ThreadPoolExecutor pool, Duration timeout) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        Loggers.UTILS.debug("gracefully shut down thread pool of {}", name);
    }

    public static int intLEFromBytes(byte[] bytes) {
        return bytes[3] << 24 | (bytes[2] & 255) << 16 | (bytes[1] & 255) << 8 | bytes[0] & 255;
    }

    private static final List<String> SCHEMES = Arrays.asList("http", "https", "ws", "wss");

    // https://blog.51cto.com/u_11440114/2987227
    public static boolean isUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        if (uri.getHost() == null) {
            return false;
        }
        return SCHEMES.contains(uri.getScheme().toLowerCase());
    }

    public static boolean checkType(String variationType, Class<?> requiredType, String returnValue) {
        if (StringUtils.isBlank(variationType) || requiredType == null || returnValue == null) {
            return false;
        }
        switch (variationType) {
            case FLAG_BOOL_TYPE:
                // bool value is generated by feature flag center
                return requiredType == Boolean.class;
            case FLAG_NUMERIC_TYPE:
                return StringUtils.isNumeric(returnValue);
            case FLAG_JSON_TYPE:
            case FLAG_STRING_TYPE:
                if (requiredType == Boolean.class) {
                    return BooleanUtils.toBooleanObject(returnValue) == null;
                }
                if (requiredType == Integer.class || requiredType == Long.class || requiredType == Double.class) {
                    return StringUtils.isNumeric(returnValue);
                }
                //others depend on the deserialization
                return true;
            default:
                return false;
        }
    }

    public static boolean isValidEnvSecret(String s) {
        if (StringUtils.isBlank(s)) {
            return false;
        }
        return CharMatcher.ascii().matchesAllOf(s);
    }

}
