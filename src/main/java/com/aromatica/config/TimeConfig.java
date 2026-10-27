package com.aromatica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Один пояс на всё приложение; main ставит его системным, чтобы timestamp без пояса совпадали
 * с часами. Сервисы берут время только из {@link Clock} — в тестах его подменяют.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Bishkek");

    @Bean
    public Clock clock() {
        return Clock.system(SHOP_ZONE);
    }
}
