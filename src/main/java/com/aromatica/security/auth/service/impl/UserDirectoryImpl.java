package com.aromatica.security.auth.service.impl;

import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.model.UserNameRow;
import com.aromatica.security.auth.repository.AppUserRepository;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserDirectoryImpl implements UserDirectory {

    AppUserRepository users;

    @Override
    public @Nullable String login(@Nullable UUID id) {
        return id == null ? null : users.findById(id).map(AppUser::getLogin).orElse(null);
    }

    @Override
    public @Nullable String name(@Nullable UUID id) {
        return id == null ? null : users.findById(id).map(UserDirectoryImpl::display).orElse(null);
    }

    @Override
    public @Nullable UUID idByLogin(@Nullable String login) {
        return login == null ? null : users.findByLogin(login).map(AppUser::getId).orElse(null);
    }

    @Override
    public Optional<AppUser> byLogin(String login) {
        return login == null ? Optional.empty() : users.findByLogin(login);
    }

    @Override
    public Optional<AppUser> byId(UUID id) {
        return id == null ? Optional.empty() : users.findById(id);
    }

    @Override
    public AppUser require(String login) {
        return byLogin(login).orElseThrow(() -> new AccessDeniedException("Пользователь не найден"));
    }

    @Override
    public Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (UserNameRow row : nameRows(ids)) {
            String displayName = row.getDisplayName();
            names.put(row.getId(), displayName != null && !displayName.isBlank() ? displayName : row.getLogin());
        }
        return names;
    }

    @Override
    public Map<UUID, String> logins(Collection<UUID> ids) {
        Map<UUID, String> logins = new HashMap<>();
        for (UserNameRow row : nameRows(ids)) {
            logins.put(row.getId(), row.getLogin());
        }
        return logins;
    }

    @Override
    public List<AppUser> activeUsers() {
        return users.findByArchivedFalse();
    }

    /** Имена без загрузки сущностей и их складов. */
    private List<UserNameRow> nameRows(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        return wanted.isEmpty() ? List.of() : users.findNames(wanted);
    }

    private static String display(AppUser u) {
        return u.displayNameOrLogin();
    }
}
