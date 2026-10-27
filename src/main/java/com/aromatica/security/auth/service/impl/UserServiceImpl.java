package com.aromatica.security.auth.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.security.auth.authentication.PasswordPolicy;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.repository.AppUserRepository;
import com.aromatica.security.auth.service.RefreshTokenService;
import com.aromatica.security.auth.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    AppUserRepository repository;
    PasswordEncoder passwordEncoder;
    RefreshTokenService refreshTokens;

    @Transactional
    @Override
    public void changeOwnPassword(
            String login,
            String current,
            String next
    ) {
        PasswordPolicy.validate(next);
        AppUser user = repository.findByLogin(login)
                .orElseThrow(() -> new ConflictException("Пользователь не найден"));
        if (!passwordEncoder.matches(current != null ? current : "", user.getPasswordHash())) {
            throw new BusinessRuleException("Текущий пароль неверен");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        // пароль меняют, когда его могли узнать: уже выданные сессии больше не действуют
        refreshTokens.revokeAll(user.getId());
    }

    @Transactional
    @Override
    public AppUser create(
            AppUser user,
            String password
    ) {
        PasswordPolicy.validate(password);
        String login = AppUser.normalizeLogin(user.getLogin());
        if (repository.findByLogin(login).isPresent()) {
            throw new BusinessRuleException("Логин уже занят: " + login);
        }
        user.setId(UUID.randomUUID());
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (user.getRole() == null) {
            user.setRole(AppUser.Role.SELLER);
        }
        return repository.save(user);
    }

    @Transactional
    @Override
    public AppUser update(
            UUID id,
            AppUser changes
    ) {
        AppUser user = get(id);
        user.setDisplayName(changes.getDisplayName());
        AppUser.Role newRole = changes.getRole();
        if (newRole != null && newRole != user.getRole()) {
            if (user.getRole() == AppUser.Role.ADMIN && isLastActiveAdmin(user)) {
                throw new ConflictException("Нельзя снять роль у последнего администратора");
            }
            user.setRole(newRole);
        }
        user.getWarehouseIds().clear();
        user.getWarehouseIds().addAll(changes.getWarehouseIds());
        return user;
    }

    @Transactional
    @Override
    public AppUser resetPassword(
            UUID id,
            String password
    ) {
        PasswordPolicy.validate(password);
        AppUser user = get(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        // новый пароль выдают, когда старый могли узнать: выданные сессии больше не действуют
        refreshTokens.revokeAll(user.getId());
        return user;
    }

    @Transactional
    @Override
    public AppUser setArchived(
            UUID id,
            boolean archived,
            String actorLogin
    ) {
        AppUser user = get(id);
        if (user.isArchived() == archived) {
            return user;
        }
        if (archived) {
            if (actorLogin != null && user.getLogin().equals(actorLogin)) {
                throw new ConflictException("Нельзя архивировать самого себя");
            }
            if (user.getRole() == AppUser.Role.ADMIN && isLastActiveAdmin(user)) {
                throw new ConflictException("Нельзя архивировать последнего администратора");
            }
        }
        user.setArchived(archived);
        if (archived) {
            // в архиве сотрудник не работает: его сессии гасим сразу, а не при следующем обновлении
            refreshTokens.revokeAll(user.getId());
        }
        return user;
    }

    /** Единственный ли это активный админ. */
    private boolean isLastActiveAdmin(AppUser candidate) {
        return !repository.existsByRoleAndArchivedFalseAndIdNot(AppUser.Role.ADMIN, candidate.getId());
    }

    private AppUser get(UUID id) {
        return repository.require(id, "Сотрудник не найден");
    }
}
