package com.aromatica.security.auth.service.impl;

import com.aromatica.security.auth.authentication.AuthProperties;
import com.aromatica.security.auth.model.RefreshToken;
import com.aromatica.security.auth.repository.RefreshTokenRepository;
import com.aromatica.security.auth.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    /** Сколько держать истёкший или отозванный токен. */
    private static final Duration KEEP_SPENT = Duration.ofDays(7);

    private final RefreshTokenRepository repository;
    private final long ttlMinutes;
    private final Clock clock;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository repository,
            AuthProperties props,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
        this.ttlMinutes = props.refreshTokenTtl().toMinutes();
    }

    @Override
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = B64.encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusMinutes(ttlMinutes));
        repository.save(token);
        return raw;
    }

    @Override
    @Transactional
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Rotation.fail("Недействительный токен обновления");
        }
        RefreshToken token = repository.findByTokenHash(hash(rawToken)).orElse(null);
        if (token == null) {
            return Rotation.fail("Недействительный токен обновления");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getRevokedAt() != null) {
            return reused(token.getUserId());
        }
        if (token.getExpiresAt().isBefore(now)) {
            return Rotation.fail("Сессия истекла — войдите заново");
        }
        // оба параллельных запроса могли прочитать токен активным, но погасит его один;
        // второй — то же повторное предъявление
        if (repository.revokeIfActive(token.getId(), now) == 0) {
            return reused(token.getUserId());
        }
        return Rotation.ok(token.getUserId(), issue(token.getUserId()));
    }

    /** Повтор погашенного токена — вероятно, кража: отзываем все, войдут заново и клиент, и вор. */
    private Rotation reused(UUID userId) {
        revokeAll(userId);
        return Rotation.fail("Сессия сброшена — войдите заново");
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllActive(userId, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public int deleteSpent() {
        return repository.deleteSpentBefore(LocalDateTime.now(clock).minus(KEEP_SPENT));
    }

    /** SHA-256 от токена в hex — в БД хранится только он. */
    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
