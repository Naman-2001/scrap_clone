package tech.dcluttr.dcluttrscrapper.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Configuration for rate limiting
 */
@Getter
@AllArgsConstructor
public class RateLimitConfig {
    
    /**
     * Maximum number of requests allowed in the period
     */
    private final double maxRequests;
    
    /**
     * Time period in seconds after which the rate limit resets
     */
    private final double refillPeriodSeconds;
    
    /**
     * Name or key prefix for this rate limiter
     */
    private final String name;
} 