package com.aromatica.security.auth.authentication;

import com.aromatica.common.error.BusinessRuleException;

import java.nio.charset.StandardCharsets;

/** Политика паролей CRM: минимум 8 символов, заглавная и строчная буквы, цифра и спецсимвол. */
public final class PasswordPolicy {

    public static final String RULES =
            "Пароль: минимум 8 символов, заглавная и строчная буквы, цифра и символ";

    private PasswordPolicy() {
    }

    /** Бросает IllegalArgumentException с описанием правил, если пароль слабый. */
    public static void validate(String password) {
        if (password == null
                || password.length() < 8
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BusinessRuleException(RULES);
        }
        // BCrypt учитывает только первые 72 байта, а новые версии Spring длиннее не принимают вовсе
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("Пароль слишком длинный: до 72 байт (около 36 русских букв)");
        }
    }
}
