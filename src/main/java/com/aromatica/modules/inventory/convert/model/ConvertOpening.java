package com.aromatica.modules.inventory.convert.model;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Вскрытие флакона или тестера: штуки уходят, на склад приходит распив в миллилитрах.
 *
 * @param targetMl         объём распива; null — объём товара × штук
 * @param targetPriceMinor цена за мл, если распив-SKU создаётся при этом вскрытии
 * @param targetName       имя распив-SKU, если создаётся
 * @param warehouseId      склад вскрытия; касса передаёт склад своей смены
 */
public record ConvertOpening(

        UUID sourceProductId,

        BigDecimal sourcePcs,

        @Nullable
        BigDecimal targetMl,

        @Nullable
        Long targetPriceMinor,

        @Nullable
        String targetName,

        @Nullable
        String comment,

        @Nullable
        UUID warehouseId
) {
}
