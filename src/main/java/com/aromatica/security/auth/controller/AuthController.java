package com.aromatica.security.auth.controller;

import com.aromatica.security.auth.dtos.AuthDtos.LoginRequest;
import com.aromatica.security.auth.dtos.AuthDtos.LoginResponse;
import com.aromatica.security.auth.dtos.AuthDtos.LogoutRequest;
import com.aromatica.security.auth.dtos.AuthDtos.PasswordChangeRequest;
import com.aromatica.security.auth.dtos.AuthDtos.RefreshRequest;
import com.aromatica.security.auth.dtos.AuthDtos.TokenPair;
import com.aromatica.security.auth.dtos.AuthDtos.UserInfo;
import com.aromatica.security.auth.endpoint.AuthEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Вход",
        description = "JWT: login отдаёт короткий access (Bearer) и refresh; когда access истёк, "
                + "refresh меняется на новую пару через /refresh. Сессий и кук нет"
)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthEndpoint authEndpoint;

    @Operation(summary = "Войти", security = {})
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http
    ) {
        return authEndpoint.login(request, http);
    }

    @Operation(
            summary = "Обновить токены",
            description = "Новая пара токенов по refresh; переданный refresh после этого недействителен",
            security = {}
    )
    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return authEndpoint.refresh(request);
    }

    @Operation(
            summary = "Выйти",
            description = "Отзывает refresh-токен; access действует до конца своего короткого срока",
            security = {}
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        authEndpoint.logout(request);
    }

    @Operation(summary = "Сменить свой пароль", description = "Текущий пароль обязателен")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authEndpoint.changePassword(request);
    }

    @Operation(summary = "Текущий пользователь")
    @GetMapping("/me")
    public UserInfo me() {
        return authEndpoint.me();
    }
}
