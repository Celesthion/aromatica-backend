package com.aromatica.security.auth.authentication;

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

/** Тормоз подбора: блокировка после порога промахов, рост паузы, сброс успехом. */
class LoginRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    /** Часы, которые берут текущий момент из now. */
    private Clock movableClock() {
        return new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
            @Override public long millis() { return now.get().toEpochMilli(); }
        };
    }

    private void advance(Duration d) {
        now.set(now.get().plus(d));
    }

    /** Попытка с неверным паролем: пропущена лимитером и осталась неудачей (успех её не списал). */
    private static void failedAttempt(
            LoginRateLimiter limiter,
            String login,
            String ip
    ) {
        assertThat(limiter.tryAttempt(login, ip)).isZero();
    }

    @Test
    void tryAttempt_locksAfterMaxFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
        // после пятого промаха вход закрыт, и закрытая попытка не считается
        assertThat(limiter.tryAttempt("admin", "1.1.1.1")).isGreaterThan(0);
    }

    /**
     * Подмена IP на каждую попытку (X-Forwarded-For, ротация прокси) не должна
     * давать свежий счётчик: логин запирается и сам, с любых адресов.
     */
    @Test
    void tryAttempt_locksLoginAcrossRotatingIps() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_LOGIN; i++) {
            failedAttempt(limiter, "admin", "10.0.0." + i);
        }
        assertThat(limiter.tryAttempt("admin", "10.0.0.250")).isGreaterThan(0);
        // другой логин не задет
        assertThat(limiter.tryAttempt("seller", "10.0.0.250")).isZero();
    }

    /** Успешный вход сбрасывает и счётчик по логину, а не только пару с этим IP. */
    @Test
    void onSuccess_clearsLoginWideCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_LOGIN - 1; i++) {
            failedAttempt(limiter, "admin", "10.0.0." + i);
        }
        assertThat(limiter.tryAttempt("admin", "10.0.0.1")).isZero();
        limiter.onSuccess("admin", "10.0.0.1");
        failedAttempt(limiter, "admin", "10.0.0.2");
        assertThat(limiter.tryAttempt("admin", "10.0.0.3")).isZero();
    }

    @Test
    void tryAttempt_unlocksAfterWaiting() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
        assertThat(limiter.tryAttempt("admin", "1.1.1.1")).isGreaterThan(0);
        advance(LoginRateLimiter.BASE_LOCK.plusSeconds(1));
        assertThat(limiter.tryAttempt("admin", "1.1.1.1")).isZero();
    }

    @Test
    void onSuccess_clearsHistory() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
        // пятая попытка с верным паролем: счётчик сброшен, следующие пять снова разрешены
        assertThat(limiter.tryAttempt("admin", "1.1.1.1")).isZero();
        limiter.onSuccess("admin", "1.1.1.1");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
    }

    @Test
    void tryAttempt_doesNotLockOtherIp() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
        assertThat(limiter.tryAttempt("admin", "2.2.2.2")).isZero();
    }

    @Test
    void tryAttempt_growsLockWithMoreFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            failedAttempt(limiter, "admin", "1.1.1.1");
        }
        long firstLock = limiter.tryAttempt("admin", "1.1.1.1");
        advance(LoginRateLimiter.BASE_LOCK.plusSeconds(1));
        failedAttempt(limiter, "admin", "1.1.1.1"); // шестой промах — пауза дольше
        long secondLock = limiter.tryAttempt("admin", "1.1.1.1");
        assertThat(secondLock).isGreaterThan(firstLock);
    }

    /**
     * Попытка считается до проверки пароля: из пачки одновременных запросов пройдёт
     * не больше порога, остальные упрутся в блокировку, не дождавшись чужих промахов.
     */
    @Test
    void tryAttempt_parallelAttemptsCannotOvershootThreshold() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(movableClock());
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger passed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.tryAttempt("admin", "1.1.1.1") == 0) {
                    passed.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(passed.get()).isEqualTo(LoginRateLimiter.MAX_FAILURES);
    }
}
