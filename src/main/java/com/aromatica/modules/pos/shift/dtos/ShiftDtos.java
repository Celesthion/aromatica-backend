package com.aromatica.modules.pos.shift.dtos;

import com.aromatica.modules.pos.shift.dtos.ShiftDtos.CloseShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.OpenShiftRequest;
import com.aromatica.modules.pos.shift.model.PosShift;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

/** Смена кассы: открытие, закрытие, журнал, сводка. Деньги в минорных единицах (тыйын). */
public final class ShiftDtos {

    private ShiftDtos() {}

    public record OpenShiftRequest(

            UUID warehouseId,

            @PositiveOrZero(message = "Размен не может быть отрицательным")
            Long openingCashMinor
    ) {
        public OpenShiftRequest {
            openingCashMinor = requireNonNullElse(openingCashMinor, 0L);
        }
    }

    public record CloseShiftRequest(

            @PositiveOrZero(message = "Пересчитанная сумма не может быть отрицательной")
            Long countedCashMinor,

            String comment
    ) {}

    public record ShiftDto(
            UUID id,
            long number,
            PosShift.Status status,
            UUID warehouseId,
            String warehouseName,
            String openedBy,
            LocalDateTime openedAt,
            long openingCashMinor,
            String closedBy,
            LocalDateTime closedAt,
            Long countedCashMinor,
            Long expectedCashMinor,
            String comment
    ) {}

    public record ShiftListResponse(
            List<ShiftDto> items,
            int page,
            int totalPages,
            long totalItems
    ) {}

    /** Админу доступны все склады, продавцу — назначенные. */
    public record WarehousePick(
            UUID id,
            String name,
            boolean isDefault
    ) {}

    /** Вклад продавца в смену: его чеки и оформленные им возвраты. */
    public record SellerLine(

            String login,

            String name,

            int receiptsCount,

            long revenueMinor,

            long returnsMinor,

            // разлив, который продавец списал при наливе
            BigDecimal writeOffMl,

            long writeOffCostMinor
    ) {}

    /** X-отчёт по проведённым чекам смены. */
    public record ShiftSummary(

            int receiptsCount,

            long revenueMinor,

            long cashMinor,

            long noCashMinor,

            int returnsCount,

            long returnsMinor,

            long expectedCashMinor,

            // 0 при пустой смене
            long avgReceiptMinor,

            // скидки построчные и на чек вместе
            long discountsMinor,

            // сумма подарков — по себестоимости
            int giftsCount,

            long giftsCostMinor,

            // проданные сертификаты — аванс: деньги в кассе есть, выручки нет;
            // наличная часть входит в нал в ящике
            int certificatesSoldCount,

            long certificatesSoldMinor,

            // погашение сертификатом: выручка есть, денег в кассе нет
            long certificatesRedeemedMinor,

            // списано кнопкой «Не хватило»: выручки нет, только убыток по себестоимости
            BigDecimal splitWriteOffMl,

            long splitWriteOffCostMinor,

            List<SellerLine> perSeller
    ) {}
}
