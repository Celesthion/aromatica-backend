package com.aromatica.security.auth.endpoint;

import com.aromatica.security.auth.dtos.AuthDtos.LoginRequest;
import com.aromatica.security.auth.dtos.AuthDtos.LoginResponse;
import com.aromatica.security.auth.dtos.AuthDtos.LogoutRequest;
import com.aromatica.security.auth.dtos.AuthDtos.PasswordChangeRequest;
import com.aromatica.security.auth.dtos.AuthDtos.RefreshRequest;
import com.aromatica.security.auth.dtos.AuthDtos.TokenPair;
import com.aromatica.security.auth.dtos.AuthDtos.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/** Вход, обновление сессии, профиль текущего пользователя. */
public interface AuthEndpoint {

    /** Неверный пароль — 401, слишком много неудач — 429. */
    LoginResponse login(LoginRequest request, HttpServletRequest http);

    /** Заменяет refresh и выдаёт новый access; плохой токен или архивный сотрудник — 401. */
    TokenPair refresh(RefreshRequest request);

    /** Выход: отзывает refresh-токен, если он передан. */
    void logout(@Nullable LogoutRequest request);

    UserInfo me();

    void changePassword(PasswordChangeRequest request);
}
