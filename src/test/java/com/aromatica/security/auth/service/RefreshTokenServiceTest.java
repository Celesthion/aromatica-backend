package com.aromatica.security.auth.service;

import com.aromatica.security.auth.authentication.AuthProperties;
import com.aromatica.security.auth.model.RefreshToken;
import com.aromatica.security.auth.repository.RefreshTokenRepository;
import com.aromatica.security.auth.service.RefreshTokenService.Rotation;
import com.aromatica.security.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh-токены: выпуск (храним только хэш), ротация со скользящим окном,
 * обнаружение повторного использования, истечение, отзыв. Данные синтетические.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final long TTL_MIN = 60;

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(repository,
                new AuthProperties(null, Duration.ofMinutes(30), Duration.ofMinutes(TTL_MIN)), Clock.systemDefaultZone());
    }

    private static String hash(String raw) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    /** Отработавшие токены держим неделю: повтор погашенного ещё распознаётся как кража. */
    @Test
    void deleteSpent_keepsLastWeek() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
        RefreshTokenServiceImpl atFixedTime = new RefreshTokenServiceImpl(repository,
                new AuthProperties(null, Duration.ofMinutes(30), Duration.ofMinutes(TTL_MIN)), fixed);
        when(repository.deleteSpentBefore(LocalDateTime.of(2026, 10, 17, 0, 0))).thenReturn(3);

        assertThat(atFixedTime.deleteSpent()).isEqualTo(3);
    }

    @Test
    void issue_storesOnlyHashNotRawToken() throws Exception {
        String raw = service.issue(USER);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getTokenHash()).isEqualTo(hash(raw));
        assertThat(saved.getTokenHash()).isNotEqualTo(raw); // сырой токен в БД не уходит
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(TTL_MIN - 1));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void rotate_revokesOldAndIssuesNew() {
        RefreshToken active = token(USER, LocalDateTime.now().plusMinutes(30), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(active));
        when(repository.revokeIfActive(eq(active.getId()), any())).thenReturn(1);

        Rotation result = service.rotate("some-raw-token");

        assertThat(result.ok()).isTrue();
        assertThat(result.userId()).isEqualTo(USER);
        assertThat(result.rawToken()).isNotBlank();
        verify(repository).revokeIfActive(eq(active.getId()), any()); // старый погашен
        verify(repository).save(any());                                // новый сохранён
    }

    @Test
    void rotate_lostRaceWithParallelRotation_treatedAsReuse() {
        // второй параллельный /refresh с тем же токеном прочитал его ещё активным,
        // но погасить уже не смог: новой пары не получает, сессии пользователя сброшены
        RefreshToken active = token(USER, LocalDateTime.now().plusMinutes(30), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(active));
        when(repository.revokeIfActive(eq(active.getId()), any())).thenReturn(0);

        Rotation result = service.rotate("same-token-twice");

        assertThat(result.ok()).isFalse();
        verify(repository).revokeAllActive(eq(USER), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_reusedRevokedToken_revokesAllAndFails() {
        RefreshToken reused = token(USER, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().minusMinutes(1)); // уже отозван (израсходован)
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(reused));

        Rotation result = service.rotate("stolen-old-token");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("сброшена");
        // похоже на кражу: гасим все активные токены пользователя и новый не выдаём
        verify(repository).revokeAllActive(eq(USER), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_expiredToken_fails() {
        RefreshToken expired = token(USER, LocalDateTime.now().minusMinutes(1), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        Rotation result = service.rotate("old-token");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("истекла");
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_unknownToken_fails() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        Rotation result = service.rotate("garbage");

        assertThat(result.ok()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void revoke_marksActiveTokenRevoked() {
        RefreshToken active = token(USER, LocalDateTime.now().plusMinutes(30), null);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(active));

        service.revoke("raw");

        assertThat(active.getRevokedAt()).isNotNull();
    }

    private static RefreshToken token(
            UUID userId,
            LocalDateTime expiresAt,
            LocalDateTime revokedAt
    ) {
        RefreshToken t = new RefreshToken();
        t.setId(UUID.randomUUID());
        t.setUserId(userId);
        t.setTokenHash("hash");
        t.setIssuedAt(LocalDateTime.now().minusMinutes(5));
        t.setExpiresAt(expiresAt);
        t.setRevokedAt(revokedAt);
        return t;
    }
}
