package com.invoicebuilder.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Distributed fixed-window rate limiter backed by Redis (the "custom Redis
 * script" option from the spec). Each key is an atomic {@code INCR}; the first
 * hit in a window sets the TTL. Fails <em>open</em> — if Redis is unreachable,
 * requests are allowed rather than locking every user out.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Records a hit against {@code key} and reports whether it is within
     * {@code limit} for the given {@code window}.
     *
     * @return {@code true} if the request is allowed, {@code false} if the
     *         limit is exceeded
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= limit;
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable for key {} — failing open", key, e);
            return true;
        }
    }
}
