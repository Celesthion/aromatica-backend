package com.aromatica.modules.pos.reservation.dtos;

import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Деньги в минорных единицах (тыйын). */
public final class ReservationDtos {

    private ReservationDtos() {}

    /** Цена приходит с фронта (текущая цена кассы) и фиксируется в брони. */
    public record ReservationLineRequest(

            @NotNull(message = "Не указан товар строки брони")
            UUID productId,

            @NotNull(message = "Не указано количество")
            @Positive(message = "Количество в строке брони должно быть больше нуля")
            BigDecimal qty,

            @PositiveOrZero(message = "Цена в строке брони не может быть отрицательной")
            long priceMinor
    ) {}

    /**
     * Бронь оформляется только на карточку покупателя, имя и телефон берутся из неё.
     * days: null — reservation.default_days, сверху ограничено max_days.
     */
    public record CreateReservationRequest(

            @NotNull(message = "Не указан склад брони")
            UUID warehouseId,

            @NotNull(message = "Выберите покупателя")
            UUID counterpartyId,

            String comment,

            Integer days,

            @NotEmpty(message = "Бронь без строк оформить нельзя")
            @Valid
            List<ReservationLineRequest> lines
    ) {}

    /** days добавляются к текущему сроку, у просроченной — к сегодняшнему дню; не больше max_days. */
    public record ExtendReservationRequest(
            Integer days
    ) {}

    /**
     * product — карточка как у витрины, но к доступному прибавлен собственный резерв брони.
     */
    public record ReservationLineDto(
            UUID productId,
            String productName,
            BigDecimal qty,
            long priceMinor,
            PosProduct product
    ) {}

    /** expired: бронь активна, но срок истёк, и резерва она уже не держит. */
    public record ReservationDto(
            UUID id,
            String customerName,
            String phone,
            UUID counterpartyId,
            String comment,
            LocalDateTime expiresAt,
            boolean expired,
            Reservation.Status status,
            long sumMinor,
            List<ReservationLineDto> lines
    ) {}
}
