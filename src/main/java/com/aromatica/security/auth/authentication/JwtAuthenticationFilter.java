package com.aromatica.security.auth.authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Пользователь грузится из БД на каждый запрос — архивных отсекает AppUserDetailsService.
 * С плохим токеном запрос идёт дальше без аутентификации, 401 вернёт цепочка фильтров.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AppUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(7)).getPayload();
                // только access: claim typ защитит от подмены, если появится другой JWT
                if (!JwtService.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
                    throw new JwtException("не access-токен");
                }
                String login = claims.getSubject();
                UserDetails user = userDetailsService.loadUserByUsername(login);
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                // просроченный токен — самая частая причина 401, фронту нужен понятный текст
                request.setAttribute(ApiAuthEntryPoint.REASON_ATTR, "Сессия истекла — войдите заново");
                SecurityContextHolder.clearContext();
            } catch (Exception e) {
                // пишем в лог, иначе попытки подобрать подпись никто не заметит
                logger.debug("Отклонён JWT: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                request.setAttribute(ApiAuthEntryPoint.REASON_ATTR, "Недействительный токен");
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
