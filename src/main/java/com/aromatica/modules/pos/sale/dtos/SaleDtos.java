package com.aromatica.modules.pos.sale.dtos;

import com.aromatica.modules.inventory.doc.dtos.DocDtos.LineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

/** Операции кассы: чек, возврат, «на полку», списание, вскрытие. Деньги в минорных единицах (тыйын). */
public final class SaleDtos {

    private static final String PAYMENT_NOT_NEGATIVE = "Сумма оплаты не может быть отрицательной";

    private SaleDtos() {}

    /**
     * qty — хвост к списанию, pouredMl — сколько вышло из флакона.
     * Хвост считает касса: только она знает, сколько уже лежит в чеке.
     */
    public record PosSplitWriteOffRequest(

            @NotNull(message = "Не указан товар для списания")
            UUID productId,

            @NotNull(message = "Не указан объём списания")
            @Positive(message = "Объём списания должен быть больше нуля")
            BigDecimal qty,

            BigDecimal pouredMl
    ) {}

    /** Строки одной вещи (масло с долей, атомайзер), которые кладут на полку до оплаты. */
    public record PosShelfRequest(

            @NotEmpty(message = "Нечего класть на полку")
            @Valid
            List<LineRequest> lines,

            String comment
    ) {}

    /** Объём кассир не задаёт: он берётся из товара, склад — из смены. */
    public record PosConvertRequest(

            @NotNull(message = "Не указан товар для вскрытия")
            UUID sourceProductId,

            @NotNull(message = "Не указано количество")
            @Positive(message = "Количество вскрываемых штук должно быть больше нуля")
            BigDecimal sourcePcs
    ) {}

    /** reservationId — продажа из брони: чек идёт за счёт её резерва. */
    public record ReceiptRequest(

            @NotEmpty(message = "Чек без строк пробить нельзя")
            @Valid
            List<LineRequest> lines,

            @PositiveOrZero(message = PAYMENT_NOT_NEGATIVE)
            Long cashMinor,

            @PositiveOrZero(message = PAYMENT_NOT_NEGATIVE)
            Long noCashMinor,

            String comment,

            UUID reservationId,

            // null — продажа без покупателя
            UUID counterpartyId,

            // платная сумма до скидки чека, база порогов акций; сервер её перепроверяет
            Long giftBaseMinor,

            // оплата сертификатом — не деньги в кассе, а погашение выданного раньше обязательства
            String certificateCode,

            @PositiveOrZero(message = PAYMENT_NOT_NEGATIVE)
            Long certificateMinor
    ) {

        public ReceiptRequest(
                List<LineRequest> lines,
                Long cashMinor,
                Long noCashMinor,
                String comment
        ) {
            this(lines, cashMinor, noCashMinor, comment, null, null, null, null, null);
        }

        public ReceiptRequest {
            cashMinor = requireNonNullElse(cashMinor, 0L);
            noCashMinor = requireNonNullElse(noCashMinor, 0L);
            certificateMinor = requireNonNullElse(certificateMinor, 0L);
        }

        public ReceiptRequest(
                List<LineRequest> lines,
                Long cashMinor,
                Long noCashMinor,
                String comment,
                UUID reservationId
        ) {
            this(lines, cashMinor, noCashMinor, comment, reservationId, null, null, null, null);
        }

        public ReceiptRequest(
                List<LineRequest> lines,
                Long cashMinor,
                Long noCashMinor,
                String comment,
                UUID reservationId,
                UUID counterpartyId
        ) {
            this(lines, cashMinor, noCashMinor, comment, reservationId, counterpartyId, null, null, null);
        }
    }

    public record ReturnRequest(

            @NotNull(message = "Не указан исходный чек")
            UUID sourceReceiptId,

            @NotEmpty(message = "Возврат без строк оформить нельзя")
            @Valid
            List<LineRequest> lines,

            @PositiveOrZero(message = PAYMENT_NOT_NEGATIVE)
            Long cashMinor,

            @PositiveOrZero(message = PAYMENT_NOT_NEGATIVE)
            Long noCashMinor,

            String comment
    ) {
        public ReturnRequest {
            cashMinor = requireNonNullElse(cashMinor, 0L);
            noCashMinor = requireNonNullElse(noCashMinor, 0L);
        }
    }
}
