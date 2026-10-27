package com.aromatica.modules.finance.gift.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GiftCodesTest {

    @Test
    void next_generatesTwelveDigitLuhnValidCode() {
        for (int i = 0; i < 500; i++) {
            String code = GiftCodes.next();
            assertThat(code).matches("\\d{12}");
            assertThat(GiftCodes.isValid(code)).isTrue();
        }
    }

    @Test
    void isValid_catchesTypoAndTransposition() {
        String code = GiftCodes.next();
        char first = code.charAt(0);
        String typo = (first == '9' ? '0' : (char) (first + 1)) + code.substring(1);
        assertThat(GiftCodes.isValid(typo)).isFalse();
        // перестановка двух соседних разных цифр; пару 0 и 9 алгоритм Луна не ловит
        for (int i = 0; i < code.length() - 2; i++) {
            char a = code.charAt(i);
            char b = code.charAt(i + 1);
            boolean zeroNine = (a == '0' && b == '9') || (a == '9' && b == '0');
            if (a != b && !zeroNine) {
                String swapped = code.substring(0, i) + code.charAt(i + 1) + code.charAt(i) + code.substring(i + 2);
                assertThat(GiftCodes.isValid(swapped)).isFalse();
                return;
            }
        }
    }

    @Test
    void normalize_stripsSpacesOnly() {
        assertThat(GiftCodes.normalize(" 7391 0284 5561 ")).isEqualTo("739102845561");
        assertThat(GiftCodes.normalize(" GC-1 ")).isEqualTo("GC-1");
    }

    @Test
    void next_thousandCodesAreDistinct() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) codes.add(GiftCodes.next());
        assertThat(codes).hasSize(1000);
    }
}
