package com.aromatica.security.auth.authentication;

import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Настройки `app.auth.*`. Без секрета {@link com.aromatica.security.auth.authentication.JwtService}
 * стартует со случайным ключом, а секрет короче 32 байт останавливает запуск: слабый ключ хуже случайного.
 */
@ConfigurationProperties("app.auth")
@Validated
public record AuthProperties(

        @Size(min = 32, message = "app.auth.jwt-secret короче 32 байт")
        String jwtSecret,

        @Name("access-token-ttl-minutes")
        @DurationUnit(ChronoUnit.MINUTES)
        @DefaultValue("30")
        Duration accessTokenTtl,

        @Name("refresh-token-ttl-minutes")
        @DurationUnit(ChronoUnit.MINUTES)
        @DefaultValue("60")
        Duration refreshTokenTtl
) {
}
