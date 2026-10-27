package com.aromatica.common.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Не больше N запросов на ключ за фиксированное окно — против серий вроде перебора кодов
 * сертификатов. Счётчик в памяти процесса: первый рубеж, а не распределённый лимит.
 */
@Component
public class RequestRateLimiter {

    /** Окно дольше этого не бывает: простоявшая столько запись больше не нужна. */
    static final Duration MAX_WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final ConcurrentMap<String, Window> windows;

    public RequestRateLimiter(Clock clock) {
        this.clock = clock;
        this.windows = ExpiringCounters.create(clock, MAX_WINDOW);
    }

    private record Window(
            long startedAt,
            int count
    ) {
    }

    /** false, если лимит на ключ в этом окне исчерпан. */
    public boolean tryAcquire(
            String key,
            int limit,
            Duration window
    ) {
        if (window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("Окно лимита длиннее " + MAX_WINDOW);
        }
        long now = clock.millis();
        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(key, (k, w) -> {
            Window current = w == null || now - w.startedAt() >= window.toMillis() ? new Window(now, 0) : w;
            if (current.count() >= limit) {
                return current;
            }
            allowed.set(true);
            return new Window(current.startedAt(), current.count() + 1);
        });
        return allowed.get();
    }
}
