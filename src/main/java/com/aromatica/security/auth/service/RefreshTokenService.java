package com.aromatica.security.auth.service;

import java.util.UUID;

/** Refresh-токен — случайная строка без структуры, в БД только хэш; срок скользящий. */
public interface RefreshTokenService {

    String issue(UUID userId);

    /**
     * Отказ возвращается, а не бросается: иначе откат отменил бы погашение при обнаружении
     * кражи. При {@code !ok()} вызывающий отвечает 401.
     */
    Rotation rotate(String rawToken);

    /** Отзывает токен при выходе; неизвестный или уже отозванный токен молча пропускается. */
    void revoke(String rawToken);

    /** Отзывает все активные токены сотрудника: смена пароля, архив, подозрение на кражу. */
    void revokeAll(UUID userId);

    /** Неделю держим отработавшие токены, чтобы повтор погашенного ещё узнавался как кража. */
    int deleteSpent();

    /** Итог ротации: новый токен или причина для 401. */
    record Rotation(
            boolean ok,
            UUID userId,
            String rawToken,
            String error
    ) {

        public static Rotation ok(
                UUID userId,
                String rawToken
        ) {
            return new Rotation(true, userId, rawToken, null);
        }

        public static Rotation fail(String error) {
            return new Rotation(false, null, null, error);
        }
    }
}
