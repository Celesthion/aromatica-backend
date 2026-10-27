package com.aromatica.security.auth.authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * Access-JWT (HS256, по умолчанию 30 минут). Без секрета в конфиге берётся случайный на время
 * процесса: безопасно, но рестарт сбрасывает сессии. Продление — {@link com.aromatica.security.auth.service.RefreshTokenService}.
 */
@Service
@Slf4j
public class JwtService {

    /** Тип токена в claim {@code typ}: наружу выпускаем только access. */
    public static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final long ttlMillis;
    private final Clock clock;

    public JwtService(
            AuthProperties props,
            Clock clock
    ) {
        this.clock = clock;
        String secret = props.jwtSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            log.error("app.auth.jwt-secret не задан или короче 32 байт — использую случайный "
                    + "секрет процесса: все сессии слетят при рестарте. "
                    + "Задайте env APP_AUTH_JWT_SECRET (>= 32 байт).");
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlMillis = props.accessTokenTtl().toMillis();
    }

    /** Access-токен сотрудника: subject = логин, claim role, claim typ = access. */
    public String issue(
            String login,
            String role
    ) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(login)
                .claim("role", role)
                .claim("typ", TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }

    /** Проверяет подпись и срок; недействительный токен — JwtException. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token);
    }
}
