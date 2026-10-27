package com.aromatica.security.auth.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 в формате {"error": "<текст>"} с причиной от JwtAuthenticationFilter:
 * фронт отличает «сессия истекла» от «нет входа».
 */
@Component
public class ApiAuthEntryPoint implements AuthenticationEntryPoint {

    /** Атрибут запроса с причиной отказа — кладёт JwtAuthenticationFilter. */
    public static final String REASON_ATTR = "com.aromatica.security.auth.reason";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object reason = request.getAttribute(REASON_ATTR);
        String message = reason != null ? reason.toString() : "Требуется вход";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // сообщения фиксированные, не из запроса, поэтому экранировать нечего
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
