package com.aromatica.common.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Тормоз частоты: лимит в окне, независимость ключей, сброс по истечении окна. */
class RequestRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    private Clock movableClock() {
        return new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
            @Override public long millis() { return now.get().toEpochMilli(); }
        };
    }

    @Test
    void allowsUpToLimit_thenRefuses() {
        RequestRateLimiter limiter = new RequestRateLimiter(movableClock());
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void tryAcquire_keysAreIndependent() {
        RequestRateLimiter limiter = new RequestRateLimiter(movableClock());
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void tryAcquire_windowResetsAfterExpiry() {
        RequestRateLimiter limiter = new RequestRateLimiter(movableClock());
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isFalse();
        now.set(now.get().plus(Duration.ofMinutes(1)));
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
    }

    /** Проверка и увеличение счётчика — один шаг: из пачки одновременных запросов пройдёт ровно лимит. */
    @Test
    void tryAcquire_parallelRequestsCannotOvershootLimit() throws Exception {
        RequestRateLimiter limiter = new RequestRateLimiter(movableClock());
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.tryAcquire("k", 10, Duration.ofMinutes(1))) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(allowed.get()).isEqualTo(10);
    }
}
