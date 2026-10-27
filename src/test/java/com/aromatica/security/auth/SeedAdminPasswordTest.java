package com.aromatica.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Хэш админа из сида V3__seed.sql должен подходить к паролю admin, иначе первый вход невозможен. */
class SeedAdminPasswordTest {

    /** Тот же литерал, что в V3__seed.sql. */
    private static final String SEED_HASH =
            "$2y$10$jPTVteL3O62lZA7/pTaC1.105u2Wun36bHv92k4.pkRr7ixTEWrxm";

    @Test
    void seedHash_matchesDefaultAdminPassword() {
        assertThat(new BCryptPasswordEncoder().matches("admin", SEED_HASH)).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("wrong", SEED_HASH)).isFalse();
    }
}
