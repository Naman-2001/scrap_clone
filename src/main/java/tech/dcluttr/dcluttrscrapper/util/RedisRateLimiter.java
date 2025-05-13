package tech.dcluttr.dcluttrscrapper.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Instant;
import java.util.Arrays;

/**
 * Rate limiter utility that uses Redis directly through Jedis client
 * This demonstrates direct Jedis usage for more control over Redis operations
 */
@Component
@Slf4j
public class RedisRateLimiter {

    private final JedisPool jedisPool;
    
    // Lua script for a sliding window rate limiter
    private static final String SLIDING_WINDOW_SCRIPT = 
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "\n" +
            "-- Remove timestamps outside the window\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "\n" +
            "-- Count requests in current window\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "\n" +
            "-- Check if we're within limit\n" +
            "local allowed = 0\n" +
            "if count < limit then\n" +
            "    -- Add current timestamp to window\n" +
            "    redis.call('ZADD', key, now, now .. '-' .. math.random())\n" +
            "    -- Set expiration for cleanup\n" +
            "    redis.call('EXPIRE', key, window / 1000)\n" +
            "    allowed = 1\n" +
            "end\n" +
            "\n" +
            "return { allowed, count }";

    @Autowired
    public RedisRateLimiter(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }
    
    /**
     * Check if request is allowed using a fixed window rate limiter
     * 
     * @param key The rate limiter key
     * @param limit Maximum requests per window
     * @param windowSeconds Window size in seconds
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowRequest(String key, int limit, int windowSeconds) {
        String redisKey = "rate-limit:fixed:" + key;
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Get current count
            String countStr = jedis.get(redisKey);
            if (countStr == null) {
                // Key doesn't exist, set it with an expiration
                jedis.setex(redisKey, windowSeconds, "1");
                return true;
            }
            int count = countStr != null ? Integer.parseInt(countStr) : 0;
            log.info("Count: {}, Redis key: {}", count, redisKey);

            if (count < limit) {
                // Increment count
                jedis.incr(redisKey);
                
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking rate limit: {}", e.getMessage(), e);
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }
    
    /**
     * Check if request is allowed using a sliding window rate limiter
     * 
     * @param key The rate limiter key
     * @param limit Maximum requests per window
     * @param windowSeconds Window size in seconds
     * @return true if request is allowed, false if rate limited
     */
    public boolean allowRequestSlidingWindow(String key, int limit, int windowSeconds) {
        String redisKey = "rate-limit:sliding:" + key;
        long now = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        
        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                SLIDING_WINDOW_SCRIPT,
                Arrays.asList(redisKey),
                Arrays.asList(String.valueOf(now), String.valueOf(windowMs), String.valueOf(limit))
            );
            
            if (result instanceof Object[]) {
                Object[] results = (Object[]) result;
                if (results.length > 0 && results[0] instanceof Long) {
                    return ((Long) results[0]) == 1L;
                }
            }
            
            // If script execution fails, fall back to simpler implementation
            return allowRequest(key, limit, windowSeconds);
        } catch (Exception e) {
            log.error("Error checking sliding window rate limit: {}", e.getMessage(), e);
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }
    
    /**
     * Implements a token bucket rate limiter with direct Jedis access
     * 
     * @param key The rate limiter key
     * @param config The rate limit configuration
     * @return true if request is allowed, false if rate limited
     */
    public boolean checkTokenBucket(String key, RateLimitConfig config) {
        String tokenKey = "rate-limit:token:" + config.getName() + ":" + key;
        String timestampKey = tokenKey + ":timestamp";
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Current time in milliseconds
            long now = System.currentTimeMillis();
            
            // Calculate rate (tokens per millisecond)
            double rate = config.getMaxRequests() / (config.getRefillPeriodSeconds() * 1000);
            
            // Get or initialize token count
            double tokens = config.getMaxRequests();
            String tokensStr = jedis.get(tokenKey);
            if (tokensStr != null) {
                tokens = Double.parseDouble(tokensStr);
            }
            
            // Get last refill timestamp
            long lastRefill = now;
            String lastRefillStr = jedis.get(timestampKey);
            if (lastRefillStr != null) {
                lastRefill = Long.parseLong(lastRefillStr);
            }
            
            // Calculate elapsed time and tokens to add
            long elapsed = Math.max(0, now - lastRefill);
            double tokensToAdd = elapsed * rate;
            
            // Refill tokens
            tokens = Math.min(config.getMaxRequests(), tokens + tokensToAdd);
            
            // Try to consume a token
            boolean allowed = false;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                allowed = true;
            }
            
            // Update Redis
            int ttl = (int) Math.ceil(config.getMaxRequests() / rate);
            jedis.setex(tokenKey, ttl, String.valueOf(tokens));
            jedis.setex(timestampKey, ttl, String.valueOf(now));
            
            if (!allowed) {
                log.debug("Rate limited for key: {}", tokenKey);
            }
            
            return allowed;
        } catch (Exception e) {
            log.error("Error checking token bucket rate limit: {}", e.getMessage(), e);
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }

    /**
     * Get the remaining capacity in the current fixed window
     * 
     * @param key The rate limiter key
     * @param limit Maximum requests per window
     * @param windowSeconds Window size in seconds
     * @return The number of requests remaining in the current window, or the full limit if the key doesn't exist
     */
    public int getRemainingCapacity(String key, int limit, int windowSeconds) {
        String redisKey = "rate-limit:fixed:" + key;
        
        try (Jedis jedis = jedisPool.getResource()) {
            // Get current count
            String countStr = jedis.get(redisKey);
            
            if (countStr == null) {
                // Key doesn't exist, so full capacity is available
                return limit;
            }
            
            int count = Integer.parseInt(countStr);
            int remaining = Math.max(0, limit - count);
            
            // Get TTL to determine when window resets
            long ttl = jedis.ttl(redisKey);
            
            if (ttl == -1) {
                // Key exists but has no TTL, set one to avoid leaks
                jedis.expire(redisKey, windowSeconds);
                ttl = windowSeconds;
            }
            
            // Log the remaining capacity and time until reset
            log.debug("Rate limit remaining: {}/{} (resets in {} seconds)", 
                    remaining, limit, ttl);
            
            return remaining;
        } catch (Exception e) {
            log.error("Error checking remaining capacity: {}", e.getMessage(), e);
            // Return full capacity if Redis is unavailable
            return limit;
        }
    }
    
    /**
     * Reset a rate limiter (for testing/admin purposes)
     * 
     * @param key The rate limiter key
     * @return true if reset successful, false otherwise
     */
    public boolean resetRateLimit(String key) {
        String redisKey = "rate-limit:fixed:" + key;
        
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.del(redisKey);
            return result > 0;
        } catch (Exception e) {
            log.error("Error resetting rate limit: {}", e.getMessage(), e);
            return false;
        }
    }
} 