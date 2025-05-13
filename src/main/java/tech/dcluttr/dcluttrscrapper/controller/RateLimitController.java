package tech.dcluttr.dcluttrscrapper.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dcluttr.dcluttrscrapper.util.RedisRateLimiter;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for rate limit management
 */
@RestController
@RequestMapping("/api/rate-limits")
@RequiredArgsConstructor
public class RateLimitController {

    private final RedisRateLimiter redisRateLimiter;
    
    // Standard rate limit configurations
    private static final Map<String, Integer[]> RATE_LIMIT_CONFIGS = new HashMap<>();
    
    static {
        // Format: key -> [limit, windowSeconds]
        RATE_LIMIT_CONFIGS.put("blinkit_scraper", new Integer[]{6500, 60});
        // Add other rate limiters as needed
    }
    
    /**
     * Get the status of all rate limiters
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRateLimitStatus() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> limiters = new HashMap<>();
        
        for (Map.Entry<String, Integer[]> entry : RATE_LIMIT_CONFIGS.entrySet()) {
            String key = entry.getKey();
            Integer[] config = entry.getValue();
            int limit = config[0];
            int windowSeconds = config[1];
            
            int remaining = redisRateLimiter.getRemainingCapacity(key, limit, windowSeconds);
            
            Map<String, Object> limiterInfo = new HashMap<>();
            limiterInfo.put("limit", limit);
            limiterInfo.put("windowSeconds", windowSeconds);
            limiterInfo.put("remaining", remaining);
            limiterInfo.put("used", limit - remaining);
            
            limiters.put(key, limiterInfo);
        }
        
        result.put("rateLimiters", limiters);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get the status of a specific rate limiter
     */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getRateLimiterStatus(@PathVariable String key) {
        if (!RATE_LIMIT_CONFIGS.containsKey(key)) {
            return ResponseEntity.notFound().build();
        }
        
        Integer[] config = RATE_LIMIT_CONFIGS.get(key);
        int limit = config[0];
        int windowSeconds = config[1];
        
        int remaining = redisRateLimiter.getRemainingCapacity(key, limit, windowSeconds);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("limit", limit);
        result.put("windowSeconds", windowSeconds);
        result.put("remaining", remaining);
        result.put("used", limit - remaining);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Reset a specific rate limiter
     */
    @PostMapping("/{key}/reset")
    public ResponseEntity<Map<String, Object>> resetRateLimiter(@PathVariable String key) {
        if (!RATE_LIMIT_CONFIGS.containsKey(key)) {
            return ResponseEntity.notFound().build();
        }
        
        boolean resetSuccessful = redisRateLimiter.resetRateLimit(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("reset", resetSuccessful);
        
        if (resetSuccessful) {
            Integer[] config = RATE_LIMIT_CONFIGS.get(key);
            result.put("limit", config[0]);
            result.put("remaining", config[0]);
        }
        
        return ResponseEntity.ok(result);
    }
} 