package com.aromatica.security.auth.service;

import com.aromatica.security.auth.model.AppUser;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Id пользователя ↔ логин или имя: в аудите UUID, наружу — читаемое. Читается вне flush-цикла
 * аудита, поэтому рекурсии, как у AuditorAware, нет.
 */
public interface UserDirectory {

    /** Логин по id (или null). */
    @Nullable String login(@Nullable UUID id);

    /** Отображаемое имя по id: displayName, иначе логин (или null). */
    @Nullable String name(@Nullable UUID id);

    /** id по логину (или null) — для сравнения прав, когда на входе логин. */
    @Nullable UUID idByLogin(@Nullable String login);

    /** Пользователь по логину — для проверок роли/доступа к складам (касса). */
    Optional<AppUser> byLogin(String login);

    /** Пользователь по id — для выпуска access при обновлении сессии (проверка архива). */
    Optional<AppUser> byId(UUID id);

    /** Пользователь по логину или 403 «Пользователь не найден» — для проверок доступа (касса, задачи). */
    AppUser require(String login);

    /** Отображаемые имена только этих пользователей, одним запросом; null в списке пропускается. */
    Map<UUID, String> names(Collection<UUID> ids);

    /** Логины только этих пользователей, одним запросом; null в списке пропускается. */
    Map<UUID, String> logins(Collection<UUID> ids);

    /** Неархивные сотрудники — например, кандидаты в исполнители задачи. */
    List<AppUser> activeUsers();
}
