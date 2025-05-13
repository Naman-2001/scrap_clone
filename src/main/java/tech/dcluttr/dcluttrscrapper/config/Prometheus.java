package tech.dcluttr.dcluttrscrapper.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Metrics;

/**
 * Prometheus metrics configuration and factory methods
 */
@Component
public class Prometheus {

    private final MeterRegistry registry;
    private final Counter activityCalls;
    private final Timer activityDuration;
    private final Counter apiCallsTotal;
    private final Timer apiCallDuration;

    // API call metrics
    private final Counter.Builder apiCallsTotalBuilder = Counter.builder("api_calls_total")
            .description("Total number of API calls made");
    
    private final Timer.Builder apiCallDurationBuilder = Timer.builder("api_call_duration")
            .description("API call duration in seconds");
    
    // Rate limiting metrics
    private final Counter.Builder rateLimitEvents = Counter.builder("rate_limit_events_total")
            .description("Total number of rate limit events");

    public Prometheus(MeterRegistry registry) {
        this.registry = registry;
        
        activityCalls = Counter.builder("activity_calls_total")
                .description("Total number of activity calls")
                .tag("activity_name", "")
                .register(registry);

        activityDuration = Timer.builder("activity_duration_seconds")
                .description("Duration of activity execution in seconds")
                .tag("activity_name", "")
                .register(registry);

        apiCallsTotal = Counter.builder("blinkit_api_calls_total")
                .description("Total number of API calls to Blinkit")
                .tag("proxy_used", "")
                .tag("status", "")
                .register(registry);

        apiCallDuration = Timer.builder("blinkit_api_call_duration_seconds")
                .description("Duration of API calls to Blinkit in seconds")
                .tag("proxy_used", "")
                .register(registry);
    }

    public Counter getActivityCalls() {
        return activityCalls;
    }

    public Timer getActivityDuration() {
        return activityDuration;
    }

    public Counter getApiCallsTotal() {
        return apiCallsTotal;
    }

    public Timer getApiCallDuration() {
        return apiCallDuration;
    }
    
    // Helper methods to create tagged metrics on demand
    public Counter.Builder activityCallsWithTags() {
        return Counter.builder("activity_calls_total")
                .description("Total number of activity calls");
    }
    
    public Timer.Builder activityDurationWithTags() {
        return Timer.builder("activity_duration_seconds")
                .description("Duration of activity execution in seconds");
    }
    
    /**
     * Create a counter for API calls with tags
     * @return The counter builder
     */
    public Counter.Builder apiCallsTotalWithTags() {
        return apiCallsTotalBuilder;
    }
    
    /**
     * Create a timer for API call duration with tags
     * @return The timer builder
     */
    public Timer.Builder apiCallDurationWithTags() {
        return apiCallDurationBuilder;
    }
    
    /**
     * Create a counter for rate limit events with tags
     * @return The counter builder
     */
    public Counter.Builder rateLimitEventsWithTags() {
        return rateLimitEvents;
    }
}
