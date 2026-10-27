package com.aromatica.modules.inventory.doc.dtos;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

/** DTO админ-API складских документов. Деньги — минорные единицы (тыйын). */
public final class DocDtos {

    private DocDtos() {}

    /**
     * Строка документа. Подарок: цена ноль, себестоимость списывается; сервер проверяет,
     * что он заработан платными строками (PosGiftValidator).
     */
    public record LineRequest(

            @NotNull(message = "Не указан товар строки")
            UUID productId,

            @NotNull(message = "Не указано количество")
            BigDecimal qty,

            @PositiveOrZero(message = "Цена не может быть отрицательной")
            long priceMinor,

            @DecimalMin(value = "0", message = "Скидка должна быть от 0 до 100%")
            @DecimalMax(value = "100", message = "Скидка должна быть от 0 до 100%")
            BigDecimal discountPct,

            /** Скидка суммой в тыйынах, основной способ; если null — считаем из discountPct. */
            @PositiveOrZero(message = "Скидка не может быть отрицательной")
            Long discountMinor,

            Boolean gift,

            UUID giftRuleId,

            String giftGroupId,

            /** Доля масла в разливе, %: строка — масло, спрей выходит qty × 100 / dilutionPct; null — не разлив. */
            @DecimalMin(value = "30", message = "Концентрация — от 30 до 100 %")
            @DecimalMax(value = "100", message = "Концентрация — от 30 до 100 %")
            BigDecimal dilutionPct,

            /** Метка вещи: строки одного флакона (масло + атомайзер) — одна метка. */
            UUID bundleId,

            /** Склад строки, если не склад документа (полка). */
            UUID warehouseId
    ) {

        /** Нет скидки — ноль, нет флага — не подарок: дальше по коду без проверок на null. */
        public LineRequest {
            discountPct = requireNonNullElse(discountPct, BigDecimal.ZERO);
            gift = requireNonNullElse(gift, Boolean.FALSE);
        }

        public LineRequest(
                UUID productId,
                BigDecimal qty,
                long priceMinor,
                BigDecimal discountPct
        ) {
            this(productId, qty, priceMinor, discountPct, null, null, null, null, null, null, null);
        }

        public LineRequest(
                UUID productId,
                BigDecimal qty,
                long priceMinor,
                BigDecimal discountPct,
                Boolean gift
        ) {
            this(productId, qty, priceMinor, discountPct, null, gift, null, null, null, null, null);
        }

        /** Форма со скидкой процентом и акцией — для старых вызовов и тестов. */
        public LineRequest(
                UUID productId,
                BigDecimal qty,
                long priceMinor,
                BigDecimal discountPct,
                Boolean gift,
                UUID giftRuleId,
                String giftGroupId
        ) {
            this(productId, qty, priceMinor, discountPct, null, gift, giftRuleId, giftGroupId, null, null, null);
        }

        /** Та же строка на другом складе (полка) — для возврата и продажи с полки. */
        public LineRequest onWarehouse(UUID warehouse) {
            return new LineRequest(productId, qty, priceMinor, discountPct, discountMinor, gift,
                    giftRuleId, giftGroupId, dilutionPct, bundleId, warehouse);
        }

        /** Скидка строки в тыйынах: прислали сумму — она; иначе — из процента (старые клиенты). */
        public long resolvedDiscountMinor() {
            return discountMinor != null ? discountMinor : CostMath.discountMinorForPct(qty, priceMinor, discountPct);
        }
    }

    public record DocRequest(

            @NotNull(message = "Не указан тип документа")
            DocType type,

            LocalDateTime moment,

            UUID warehouseId,

            UUID warehouseToId,

            UUID counterpartyId,

            String comment,

            @PositiveOrZero(message = "Сумма оплаты не может быть отрицательной")
            Long cashMinor,

            @PositiveOrZero(message = "Сумма оплаты не может быть отрицательной")
            Long noCashMinor,

            UUID payAccountId,

            @Valid
            List<LineRequest> lines
    ) {
        public DocRequest {
            cashMinor = requireNonNullElse(cashMinor, 0L);
            noCashMinor = requireNonNullElse(noCashMinor, 0L);
        }
    }

    public record LineDto(

            UUID id,

            UUID productId,

            String productName,

            /** Форма товара — по ней чек отличает, например, парфюмерную воду. */
            String productKind,

            BigDecimal qty,

            long priceMinor,

            /** Справочный процент (2 знака); считать по нему нельзя — см. discountMinor. */
            BigDecimal discountPct,

            /** Скидка строки суммой в тыйынах — источник правды. */
            long discountMinor,

            Long costMinor,

            long sumMinor,

            int lineNo,

            boolean gift,

            /** Доля масла в разливе, проценты; null — строка не про разлив. */
            BigDecimal dilutionPct,

            /** Метка вещи (флакон) — строки с одной меткой показываются одной. */
            UUID bundleId,

            /** Склад строки, если не склад документа (полка). */
            UUID warehouseId
    ) {}

    public record DocDto(
            UUID id,
            DocType docType,
            long number,
            DocStatus status,
            LocalDateTime moment,
            UUID warehouseId,
            UUID warehouseToId,
            UUID counterpartyId,
            String counterpartyName,
            long sumMinor,
            String comment,
            long cashMinor,
            long noCashMinor,
            UUID payAccountId,
            LocalDateTime createdAt,
            LocalDateTime postedAt,
            String createdBy,
            List<LineDto> lines
    ) {}

    public record DocListItem(
            UUID id,
            DocType docType,
            long number,
            DocStatus status,
            LocalDateTime moment,
            String counterpartyName,
            long sumMinor,
            int linesCount
    ) {}

    /** login — отображаемое имя актора (резолвится из actor_id). */
    public record EventDto(
            String action,
            String login,
            LocalDateTime at
    ) {}

}
