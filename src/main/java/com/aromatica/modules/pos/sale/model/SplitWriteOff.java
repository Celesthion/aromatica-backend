package com.aromatica.modules.pos.sale.model;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Списание хвоста разлива.
 *
 * @param qty      сколько миллилитров списать
 * @param pouredMl сколько налили на самом деле; пишется в комментарий
 */
public record SplitWriteOff(

        UUID productId,

        BigDecimal qty,

        @Nullable
        BigDecimal pouredMl
) {
}
