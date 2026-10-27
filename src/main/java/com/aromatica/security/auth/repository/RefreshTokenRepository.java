package com.aromatica.security.auth.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.security.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends EntityRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Атомарно: из двух параллельных ротаций одного токена строку обновит одна, вторая получит 0. */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id = :id and r.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Удаляет токены, истёкшие или отозванные раньше этого момента. */
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :before or r.revokedAt < :before")
    int deleteSpentBefore(@Param("before") LocalDateTime before);

    /** Отозвать все активные токены пользователя (реакция на кражу / архивацию). */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now "
            + "where r.userId = :userId and r.revokedAt is null")
    int revokeAllActive(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
