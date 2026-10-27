package com.aromatica.security.auth.authentication;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Автор для аудита — из {@link AppUserPrincipal}, без запросов в БД: иначе autoflush внутри
 * аудита рекурсивно вызывал бы листенер. Без аутентификации — null.
 */
@Component
public class SecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p.getId());
        }
        return Optional.empty();
    }
}
