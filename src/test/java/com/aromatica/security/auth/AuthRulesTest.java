package com.aromatica.security.auth;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.security.auth.authentication.PasswordPolicy;
import com.aromatica.security.auth.dtos.AuthDtos.LoginRequest;
import com.aromatica.security.auth.model.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Мелкие правила сотрудника: логин, пароль, отображаемое имя. */
class AuthRulesTest {

    @Test
    void loginRequest_normalizesLogin() {
        // при создании логин хранится в нижнем регистре, значит и вход должен его так искать
        assertThat(new LoginRequest("  Ivan ", "x").login()).isEqualTo("ivan");
    }

    @Test
    void validate_rejectsPasswordLongerThanBcryptLimit() {
        String cyrillic = "Пароль-1" + "я".repeat(40); // больше 72 байт в UTF-8
        assertThatThrownBy(() -> PasswordPolicy.validate(cyrillic))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("72 байт");
        assertThatCode(() -> PasswordPolicy.validate("Secret-123")).doesNotThrowAnyException();
    }

    @Test
    void displayNameOrLogin_fallsBackToLoginWhenBlank() {
        AppUser user = new AppUser();
        user.setLogin("ivan");
        user.setDisplayName(" ");
        assertThat(user.displayNameOrLogin()).isEqualTo("ivan");
        user.setDisplayName("Иван");
        assertThat(user.displayNameOrLogin()).isEqualTo("Иван");
    }
}
