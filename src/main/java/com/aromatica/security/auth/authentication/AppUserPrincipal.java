package com.aromatica.security.auth.authentication;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/** Spring {@link User} с id: аудит берёт автора из SecurityContext, не обращаясь к БД внутри flush. */
public class AppUserPrincipal extends User {

    private final UUID id;

    public AppUserPrincipal(
            UUID id,
            String login,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(login, passwordHash, authorities);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
