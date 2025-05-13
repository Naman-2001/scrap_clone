package tech.dcluttr.dcluttrscrapper.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages state for rate limiting and other scraper features using Redis
 */
@Component
@Slf4j
public class StateManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Lua script for token bucket algorithm in Redis
    private static final String RATE_LIMIT_SCRIPT = 
            "local token_key = KEYS[1]\n" +
            "local timestamp_key = KEYS[1] .. ':timestamp'\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local rate = tonumber(ARGV[2])  -- tokens per millisecond\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local requested = tonumber(ARGV[4])\n" +
            "\n" +
            "-- Get or create current token count\n" +
            "local tokens = tonumber(redis.call('get', token_key))\n" +
            "if tokens == nil then\n" +
            "    tokens = capacity\n" +
            "    redis.call('set', timestamp_key, now)\n" +
            "end\n" +
            "\n" +
            "-- Get last refill timestamp\n" +
            "local last_timestamp = tonumber(redis.call('get', timestamp_key))\n" +
            "if last_timestamp == nil then\n" +
            "    last_timestamp = now\n" +
            "    redis.call('set', timestamp_key, now)\n" +
            "end\n" +
            "\n" +
            "-- Calculate elapsed time and tokens to add\n" +
            "local elapsed = math.max(0, now - last_timestamp)\n" +
            "local tokens_to_add = elapsed * rate\n" +
            "\n" +
            "-- Refill tokens\n" +
            "tokens = math.min(capacity, tokens + tokens_to_add)\n" +
            "redis.call('set', timestamp_key, now)\n" +
            "\n" +
            "-- Check if enough tokens and consume if possible\n" +
            "local allowed = 0\n" +
            "if tokens >= requested then\n" +
            "    tokens = tokens - requested\n" +
            "    allowed = 1\n" +
            "end\n" +
            "\n" +
            "-- Store updated token count and return result\n" +
            "redis.call('set', token_key, tokens)\n" +
            "local ttl = math.ceil(capacity / rate)  -- TTL based on how long it takes to refill\n" +
            "redis.call('expire', token_key, ttl)\n" +
            "redis.call('expire', timestamp_key, ttl)\n" +
            "return allowed";
    
    private final DefaultRedisScript<Long> rateLimitScript;
    
    @Autowired
    public StateManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(RATE_LIMIT_SCRIPT);
        this.rateLimitScript.setResultType(Long.class);
    }
    
    /**
     * Check if a request can proceed based on rate limit using Redis
     * 
     * @param config The rate limit configuration
     * @param key The key to identify this rate limiter (e.g., API endpoint)
     * @return true if the request can proceed, false if rate limited
     */
    public boolean checkRateLimit(RateLimitConfig config, String key) {
        String bucketKey = "rate-limit:" + config.getName() + ":" + key;
        
        // Current time in milliseconds
        long now = System.currentTimeMillis();
        
        // Calculate rate (tokens per millisecond)
        double rate = config.getMaxRequests() / (config.getRefillPeriodSeconds() * 1000);
        
        // Execute the token bucket algorithm in Redis
        List<String> keys = Collections.singletonList(bucketKey);
        Long result = redisTemplate.execute(
                rateLimitScript,
                keys, 
                String.valueOf(config.getMaxRequests()),  // capacity
                String.valueOf(rate),                     // rate (tokens per ms)
                String.valueOf(now),                     // current time
                "1"                                      // tokens to consume
        );
        
        boolean allowed = result != null && result == 1;
        
        if (!allowed) {
            log.debug("Rate limited for key: {}", bucketKey);
        }
        
        return allowed;
    }
} 