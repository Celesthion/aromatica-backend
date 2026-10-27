package com.aromatica.security.auth.authentication;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Текущий пользователь — для проверок, где @PreAuthorize мало: правило зависит от данных
 * (кассовый чек — только админу, потолок скидки — не админу).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Без входа сюда не попасть: такие маршруты закрыты Spring Security. */
    public static String login() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new AuthenticationCredentialsNotFoundException("Не авторизован");
        }
        return auth.getName();
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authority = "ROLE_" + role;
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
