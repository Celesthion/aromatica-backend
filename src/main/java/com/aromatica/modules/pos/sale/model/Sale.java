package com.aromatica.modules.pos.sale.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Продажа на кассе. Оплата — наличные, перевод и сертификат вместе должны сойтись с суммой строк.
 *
 * @param reservationId   продажа из брони: чек идёт за счёт её резерва, бронь станет SOLD
 * @param counterpartyId  покупатель; null — продажа без покупателя
 * @param giftBaseMinor   платная сумма до скидки чека, база порогов акций; сервер её перепроверяет
 * @param certificateCode код бланка, если часть оплаты — сертификатом
 */
public record Sale(

        List<ReceiptLine> lines,

        long cashMinor,

        long noCashMinor,

        long certificateMinor,

        @Nullable
        String certificateCode,

        @Nullable
        String comment,

        @Nullable
        UUID reservationId,

        @Nullable
        UUID counterpartyId,

        @Nullable
        Long giftBaseMinor
) {
}
