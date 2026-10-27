package com.aromatica.modules.pos.shift.model;

import org.jspecify.annotations.Nullable;

/**
 * Закрытие смены.
 *
 * @param countedCashMinor пересчитанный нал; null — не пересчитывали, корректировки не будет
 */
public record ShiftClosing(

        @Nullable
        Long countedCashMinor,

        @Nullable
        String comment
) {
}
