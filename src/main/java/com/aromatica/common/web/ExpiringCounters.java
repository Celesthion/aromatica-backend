package com.aromatica.common.web;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Счётчики лимитеров в памяти процесса, запись исчезает после простоя. Менять только через
 * {@code compute}: проверка и инкремент одним шагом, параллельные запросы не проскочат порог.
 * Размер не ограничен: вытеснение по размеру позволило бы сбросить чужой счётчик, наплодив ключей.
 */
public final class ExpiringCounters {

    private ExpiringCounters() {
    }

    public static <V> ConcurrentMap<String, V> create(
            Clock clock,
            Duration idle
    ) {
        return Caffeine.newBuilder()
                .expireAfterAccess(idle)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .<String, V>build()
                .asMap();
    }
}
