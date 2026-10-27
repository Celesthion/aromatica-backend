package com.aromatica.security.auth.endpoint.impl;

import com.aromatica.common.error.NotFoundException;
import com.aromatica.common.web.TooManyRequestsException;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.authentication.JwtService;
import com.aromatica.security.auth.authentication.LoginRateLimiter;
import com.aromatica.security.auth.dtos.AuthDtos.LoginRequest;
import com.aromatica.security.auth.dtos.AuthDtos.LoginResponse;
import com.aromatica.security.auth.dtos.AuthDtos.LogoutRequest;
import com.aromatica.security.auth.dtos.AuthDtos.PasswordChangeRequest;
import com.aromatica.security.auth.dtos.AuthDtos.RefreshRequest;
import com.aromatica.security.auth.dtos.AuthDtos.TokenPair;
import com.aromatica.security.auth.dtos.AuthDtos.UserInfo;
import com.aromatica.security.auth.endpoint.AuthEndpoint;
import com.aromatica.security.auth.mapper.UserMapper;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.RefreshTokenService.Rotation;
import com.aromatica.security.auth.service.RefreshTokenService;
import com.aromatica.security.auth.service.UserDirectory;
import com.aromatica.security.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthEndpointImpl implements AuthEndpoint {

    AuthenticationManager authenticationManager;
    JwtService jwtService;
    RefreshTokenService refreshTokenService;
    UserService userService;
    UserDirectory users;
    LoginRateLimiter loginRateLimiter;
    UserMapper userMapper;

    @Override
    @Transactional
    public LoginResponse login(
            LoginRequest request,
            HttpServletRequest http
    ) {
        // за прокси адрес клиента восстанавливает Tomcat (server.forward-headers-strategy)
        String ip = http.getRemoteAddr();
        // попытка считается заранее, верный пароль её спишет: параллельные запросы порог не обойдут
        long lockedFor = loginRateLimiter.tryAttempt(request.login(), ip);
        if (lockedFor > 0) {
            throw new TooManyRequestsException(
                    "Слишком много попыток входа. Повторите через " + lockedFor + " с", lockedFor);
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login(), request.password()));
        } catch (AuthenticationException e) {
            // не подсказываем, что именно неверно: логин или пароль
            throw new BadCredentialsException("Неверный логин или пароль");
        }
        loginRateLimiter.onSuccess(request.login(), ip);
        // раз authenticate прошёл, пользователь существует и не в архиве (см. AppUserDetailsService)
        AppUser user = users.byLogin(request.login()).orElseThrow();
        String access = jwtService.issue(user.getLogin(), user.getRole().name());
        String refresh = refreshTokenService.issue(user.getId());
        return userMapper.toLoginResponse(access, refresh, user);
    }

    /** Без @Transactional: иначе 401 ниже откатил бы погашение старого токена и признак кражи. */
    @Override
    public TokenPair refresh(RefreshRequest request) {
        Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        if (!rotation.ok()) {
            throw new BadCredentialsException(rotation.error());
        }
        AppUser user = users.byId(rotation.userId())
                .filter(u -> !u.isArchived())
                .orElse(null);
        if (user == null) {
            // сотрудника архивировали или удалили между сессиями: только что выданный refresh отзываем
            refreshTokenService.revoke(rotation.rawToken());
            throw new BadCredentialsException("Учётная запись недоступна");
        }
        String access = jwtService.issue(user.getLogin(), user.getRole().name());
        return userMapper.toTokenPair(access, rotation.rawToken());
    }

    @Override
    @Transactional
    public void logout(@Nullable LogoutRequest request) {
        if (request != null) {
            refreshTokenService.revoke(request.refreshToken());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfo me() {
        // удалённому или архивному роль по умолчанию не отдаём: от /me зависит, что покажет интерфейс
        return users.byLogin(CurrentUser.login())
                .map(userMapper::toInfo)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    @Override
    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        userService.changeOwnPassword(CurrentUser.login(), request.current(), request.next());
    }
}
