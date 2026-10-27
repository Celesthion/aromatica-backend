package com.aromatica.modules.inventory.convert.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

public final class ConvertDtos {

    private ConvertDtos() {}

    /** Без {@code warehouseId} — склад по умолчанию (админка); касса передаёт склад смены, иначе спишет не с той точки. */
    public record ConvertRequest(

            @NotNull(message = "Не указан товар для вскрытия")
            UUID sourceProductId,

            @Positive(message = "Количество вскрываемых штук должно быть больше нуля")
            BigDecimal sourcePcs,

            @Positive(message = "Объём распива должен быть больше нуля")
            BigDecimal targetMl,

            @PositiveOrZero(message = "Цена за мл не может быть отрицательной")
            Long targetPriceMinor,

            String targetName,

            String comment,

            UUID warehouseId
    ) {
        public ConvertRequest {
            sourcePcs = requireNonNullElse(sourcePcs, BigDecimal.ONE);
        }
    }

    /** Распив аромата: есть ли уже SKU и по какой цене, а если нет — какое имя предложить. */
    public record SplitInfo(
            boolean exists,
            UUID productId,
            String name,
            Long priceMinor,
            String suggestedName
    ) {}
}
