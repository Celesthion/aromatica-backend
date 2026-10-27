package com.aromatica.common.util;

public final class Strings {

    private Strings() {
    }

    /** Пустая или из одних пробелов — то же, что не указана; иначе без пробелов по краям. */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
