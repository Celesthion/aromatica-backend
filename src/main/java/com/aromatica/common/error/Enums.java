package com.aromatica.common.error;

/** Разбор значения перечисления из запроса: опечатка клиента — 400 с понятным текстом, а не 500. */
public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> E parse(
            Class<E> type,
            String value,
            String message
    ) {
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException(message);
        }
    }
}
