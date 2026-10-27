package com.aromatica.modules.pos.shift.model;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Открытие смены.
 *
 * @param warehouseId      склад кассы; null — склад по умолчанию
 * @param openingCashMinor размен в кассе на начало смены
 */
public record ShiftOpening(

        @Nullable
        UUID warehouseId,

        long openingCashMinor
) {
}
