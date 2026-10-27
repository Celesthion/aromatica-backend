package com.aromatica.security.auth.dtos;

import com.aromatica.security.auth.model.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO входа/профиля/обновления сессии. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(

            @NotBlank(message = "Укажите логин")
            @Size(max = 128)
            String login,

            @NotBlank(message = "Укажите пароль")
            @Size(max = 255)
            String password
    ) {
        public LoginRequest {
            login = AppUser.normalizeLogin(login);
        }
    }

    public record UserInfo(
            String id,
            String login,
            String displayName,
            String role
    ) {}

    /** refresh клиент должен хранить надёжно. */
    public record LoginResponse(
            String accessToken,
            String refreshToken,
            UserInfo user
    ) {}

    /** Ответ на обновление: новая пара токенов, refresh каждый раз заменяется. */
    public record TokenPair(
            String accessToken,
            String refreshToken
    ) {}

    public record RefreshRequest(

            @NotBlank(message = "Нет токена обновления")
            String refreshToken
    ) {}

    /** Выход: если передан refresh, отзываем его; иначе токены просто удаляет клиент. */
    public record LogoutRequest(
            String refreshToken
    ) {}

    /** Новый пароль дополнительно проверяет PasswordPolicy (в UserService). */
    public record PasswordChangeRequest(

            @NotBlank(message = "Укажите текущий пароль")
            String current,

            @NotBlank(message = "Укажите новый пароль")
            String next
    ) {}
}
