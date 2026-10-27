package com.aromatica.modules.finance.gift.service.impl;

import java.security.SecureRandom;

/**
 * 11 криптослучайных цифр + контрольная по Луну: никаких угадываемых частей, сокращающих перебор.
 * На бланке печатаем группами по четыре ({@code 7391 0284 5561}), в базе храним слитно.
 */
final class GiftCodes {

    static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private GiftCodes() {
    }

    static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH - 1; i++) {
            sb.append((char) ('0' + RANDOM.nextInt(10)));
        }
        sb.append(luhnCheckDigit(sb));
        return sb.toString();
    }

    /** Ловит опечатку в одной цифре и перестановку соседних ещё до запроса в базу. */
    static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH || !code.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return luhnCheckDigit(code.subSequence(0, LENGTH - 1)) == code.charAt(LENGTH - 1);
    }

    /** Старые коды с дефисами не трогает. */
    static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s", "");
    }

    private static char luhnCheckDigit(CharSequence payload) {
        int sum = 0;
        boolean dbl = true; // идём справа налево, цифра рядом с контрольной удваивается
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return (char) ('0' + (10 - sum % 10) % 10);
    }
}
