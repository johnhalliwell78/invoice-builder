package com.invoicebuilder.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void setsExpiryOnFirstHitAndAllowsUnderLimit() {
        when(valueOps.increment("rl:login:a@x.io")).thenReturn(1L);

        boolean allowed = service.tryAcquire("rl:login:a@x.io", 5, WINDOW);

        assertThat(allowed).isTrue();
        verify(redis).expire("rl:login:a@x.io", WINDOW);
    }

    @Test
    void allowsExactlyUpToLimitThenBlocks() {
        when(valueOps.increment("k")).thenReturn(5L, 6L);

        assertThat(service.tryAcquire("k", 5, WINDOW)).isTrue();   // 5th request
        assertThat(service.tryAcquire("k", 5, WINDOW)).isFalse();  // 6th blocked
    }

    @Test
    void doesNotResetExpiryOnSubsequentHits() {
        when(valueOps.increment("k")).thenReturn(3L);

        service.tryAcquire("k", 5, WINDOW);

        verify(redis, never()).expire(eq("k"), any(Duration.class));
    }

    @Test
    void failsOpenWhenRedisThrows() {
        when(valueOps.increment("k")).thenThrow(new RuntimeException("redis down"));

        assertThat(service.tryAcquire("k", 5, WINDOW)).isTrue();
    }
}
