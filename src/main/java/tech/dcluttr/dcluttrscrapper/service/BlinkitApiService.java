package tech.dcluttr.dcluttrscrapper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.dcluttr.dcluttrscrapper.util.RateLimitConfig;
import tech.dcluttr.dcluttrscrapper.util.RedisRateLimiter;
import tech.dcluttr.dcluttrscrapper.util.StateManager;
import tech.dcluttr.dcluttrscrapper.config.Prometheus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;
import okhttp3.Credentials;
import okhttp3.Authenticator;
import okhttp3.Route;
import okhttp3.ResponseBody;
import okhttp3.MediaType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlinkitApiService {

    private final StateManager stateManager;
    private final RedisRateLimiter redisRateLimiter;
    private final Prometheus prometheus;
    private final MeterRegistry meterRegistry;

    // Create a new ObjectMapper instance for JSON parsing
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scraper.proxy.username}")
    private String proxyUsername;

    @Value("${scraper.proxy.password}")
    private String proxyPassword;

    @Value("${scraper.proxy.dns}")
    private String proxyDns;

    private static final String BASE_URL = "https://api2.grofers.com";
    private static final int TIMEOUT = 30000; // 30 seconds

    // Fixed window rate limit configuration - 6500 requests per 60 seconds
    private static final int RATE_LIMIT_MAX_REQUESTS = 20000;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final String RATE_LIMIT_KEY = "blinkit_scraper";

    // Keep the token bucket config for backward compatibility
    private static final RateLimitConfig RATE_LIMIT_CONFIG = new RateLimitConfig(6500, 60.0, "blinkit_scraper");

    // List of user agents to rotate
    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0"
    );

    // Create a default OkHttpClient instance
    private final OkHttpClient defaultClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .build();

    /**
     * Fetch products by category with retry mechanism
     */
    public Map<String, Object> fetchProductsWithRetry(double lat, double lon, int category, int subcategory, int page) throws InterruptedException, IOException {
        // Build URL with parameters
        HttpUrl url = HttpUrl.parse(BASE_URL + "/v1/listing/widgets").newBuilder()
                .addQueryParameter("l0_cat", String.valueOf(category))
                .addQueryParameter("l1_cat", String.valueOf(subcategory))
                .addQueryParameter("page", String.valueOf(page))
                .build();

        // Create request with headers
        Request request = createRequest(url.toString(), lat, lon);

        boolean isRateLimited = !redisRateLimiter.allowRequest(RATE_LIMIT_KEY, RATE_LIMIT_MAX_REQUESTS, RATE_LIMIT_WINDOW_SECONDS);
        if (isRateLimited) {
            log.info("Rate limit reached, waiting for {} seconds", RATE_LIMIT_WINDOW_SECONDS);
            Thread.sleep(Duration.ofSeconds(10)); // Wait before retrying
            return fetchProductsWithRetry(lat, lon, category, subcategory, page);
        }

        try (Response response = defaultClient.newCall(request).execute()) {
            int statusCode = response.code();

            if (statusCode == 429 || statusCode == 403) {
                log.warn("Request blocked or rate limited. Using proxies");
                try (Response proxyResponse = createProxyClient().newCall(request).execute()) {
                    int proxyStatusCode = proxyResponse.code();
                    if (proxyStatusCode == 403 || proxyStatusCode == 429) {
                        log.error("Proxy request blocked or rate limited,trying direct: {}", proxyStatusCode);
                        return fetchProductsWithRetry(lat, lon, category, subcategory, page);
                    }
                    if (proxyStatusCode == 404) {
                        log.error("Proxy request failed with status {}", proxyStatusCode);
                        return Map.of("error", "Not Found");
                    }
                    if (proxyStatusCode >= 500) {
                        log.error("Proxy request failed with status {}", proxyStatusCode);
                        return Map.of("error", "Request Failed");
                    }
                    if (!proxyResponse.isSuccessful()) {
                        log.error("Proxy request failed with status {}", proxyStatusCode);
                        return Map.of("error", "Request Failed");
                    }
                    try (ResponseBody responseBody = proxyResponse.body()) {
                        if (responseBody == null) {
                            return Map.of("error", "Null response body");
                        }
                        String responseString = responseBody.string();
                        log.info("Response via proxy: {}", response.code());
                        return objectMapper.readValue(responseString, Map.class);
                    }
                }
            }
            if (statusCode == 404) {
                log.error("Request failed with status {}", statusCode);
                return Map.of("error", "Not Found");
            }
            if (statusCode >= 500) {
                log.error("Server error: {}", statusCode);
                return Map.of("error", "Server Error");
            }
            if (!response.isSuccessful()) {
                log.error("Request failed with status {}", statusCode);
                return Map.of("error", "Request Failed");
            }
            try (ResponseBody responseBody = response.body()) {
                if (responseBody == null) {
                    return Map.of("error", "Null response body");
                }
                String responseString = responseBody.string();
                log.info("Response via proxy: {}", response.code());
                return objectMapper.readValue(responseString, Map.class);
            }

        }

    }

    /**
     * Create an OkHttp request with necessary headers
     */
    private Request createRequest(String url, double lat, double lon) {
        Random random = new Random();
        String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

        return new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Referer", "https://blinkit.com/cn/chips-crisps/cid/1237/940")
                .header("Content-Type", "application/json")
                .header("app_client", "consumer_web")
                .header("device_id", UUID.randomUUID().toString())
                .header("app_version", "52434332")
                .header("web_app_version", "1008010016")
                .header("lat", String.valueOf(lat))
                .header("lon", String.valueOf(lon))
                .header("Connection", "keep-alive")
                .header("Priority", "u=4")
                .build();
    }


    private OkHttpClient createProxyClient() {
        try {
            // Parse proxy details
            String[] proxyParts = proxyDns.split(":");
            String proxyHost = proxyParts[0];
            int proxyPort = Integer.parseInt(proxyParts[1]);

            // Create an authenticator for proxy authentication
            Authenticator proxyAuthenticator = null;
            if (proxyUsername != null && !proxyUsername.isEmpty() &&
                    proxyPassword != null && !proxyPassword.isEmpty()) {
                proxyAuthenticator = new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) {
                        String credential = Credentials.basic(proxyUsername, proxyPassword);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    }
                };
            }

            // Create a proxy-enabled OkHttpClient
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                    .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                    .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));

            if (proxyAuthenticator != null) {
                clientBuilder.proxyAuthenticator(proxyAuthenticator);
            }

            return clientBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create proxy client", e);
        }
    }
}
