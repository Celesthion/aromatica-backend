package com.aromatica.security.auth.authentication;

import com.aromatica.common.web.ExpiringCounters;
import com.aromatica.security.auth.model.AppUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

/**
 * Защита от подбора пароля: растущая пауза по паре «логин + IP» и по логину с любых адресов —
 * иначе подбирали бы, меняя IP. Попытка считается до проверки пароля и списывается при успехе,
 * чтобы параллельные запросы не обошли порог. Счётчики в памяти процесса, не распределённые.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    /** Выше парного, чтобы чужие ошибки не блокировали сотрудника сразу. */
    static final int MAX_FAILURES_PER_LOGIN = 20;
    /** Базовая пауза; каждая следующая неудача удваивает её, но не выше потолка. */
    static final Duration BASE_LOCK = Duration.ofMinutes(1);
    static final Duration MAX_LOCK = Duration.ofMinutes(15);
    /** Запись без активности дольше этого срока забывается (дольше самой длинной паузы). */
    private static final Duration STALE = Duration.ofMinutes(30);

    private final Clock clock;
    private final ConcurrentMap<String, Attempts> attempts;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
        this.attempts = ExpiringCounters.create(clock, STALE);
    }

    /** Неудачи подряд и до какого момента вход закрыт (epoch millis). */
    private record Attempts(
            int failures,
            long lockedUntil
    ) {

        static final Attempts NONE = new Attempts(0, 0);

        long lockedSeconds(long now) {
            return lockedUntil > now ? (lockedUntil - now + 999) / 1000 : 0;
        }

        Attempts plusFailure(
                int threshold,
                long now
        ) {
            int next = failures + 1;
            if (next < threshold) {
                return new Attempts(next, lockedUntil);
            }
            long factor = 1L << Math.min(next - threshold, 20);
            return new Attempts(next, now + Math.min(BASE_LOCK.toMillis() * factor, MAX_LOCK.toMillis()));
        }
    }

    private static String normalize(String login) {
        return login == null ? "" : AppUser.normalizeLogin(login);
    }

    private static String key(
            String login,
            String ip
    ) {
        return normalize(login) + ' ' + (ip == null ? "" : ip);
    }

    /** Без IP: сменой адреса не обойти. */
    private static String loginKey(String login) {
        return normalize(login) + " *";
    }

    /**
     * Сколько секунд ждать, если вход закрыт (попытка не считается); иначе заранее пишет
     * неудачу и возвращает 0 — верный пароль спишет её через {@link #onSuccess}.
     */
    public long tryAttempt(
            String login,
            String ip
    ) {
        long now = clock.millis();
        long locked = attempt(key(login, ip), MAX_FAILURES, now);
        return locked > 0 ? locked : attempt(loginKey(login), MAX_FAILURES_PER_LOGIN, now);
    }

    /** Проверка блокировки и запись неудачи — одним шагом над записью ключа. */
    private long attempt(
            String key,
            int threshold,
            long now
    ) {
        long[] locked = new long[1];
        attempts.compute(key, (k, a) -> {
            Attempts current = a != null ? a : Attempts.NONE;
            locked[0] = current.lockedSeconds(now);
            return locked[0] > 0 ? current : current.plusFailure(threshold, now);
        });
        return locked[0];
    }

    /** Успешный вход сбрасывает оба счётчика: по паре и по логину. */
    public void onSuccess(
            String login,
            String ip
    ) {
        attempts.remove(key(login, ip));
        attempts.remove(loginKey(login));
    }
}
