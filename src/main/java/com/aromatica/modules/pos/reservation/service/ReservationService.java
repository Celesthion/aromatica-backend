package com.aromatica.modules.pos.reservation.service;

import com.aromatica.modules.pos.reservation.model.Reservation;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Бронь товара. Резерв мягкий: остатки склада не меняются, касса вычитает брони из доступного.
 */
public interface ReservationService {

    /** Остаток товара на складе и сколько из него держат брони. Доступно может уйти в минус. */
    record Availability(
            BigDecimal onHand,
            BigDecimal reserved
    ) {

        public BigDecimal available() {
            return onHand.subtract(reserved);
        }
    }

    /**
     * Цены строк фиксируются как пришли; каждой строке должно хватать остатка за вычетом других броней.
     */
    Reservation create(Reservation reservation, @Nullable Integer days);

    /** Просроченную тоже можно отменить. */
    void cancel(UUID id);

    /** Дни добавляются к текущему сроку, у просроченной — к сегодня; после продления она снова резервирует. */
    Reservation extend(UUID id, Integer days);

    /** Касса зовёт после проведения чека. */
    void markSold(UUID id, UUID docId);

    /** Сторно отменяет чек целиком, поэтому бронь возвращается клиенту. */
    void restoreAfterVoid(UUID docId);

    /** Открытые: действующие и просроченные; проданные и отменённые не отдаём. */
    List<Reservation> list(UUID warehouseId);

    /** Помечает истёкшие брони статусом EXPIRED; возвращает, сколько закрыто. */
    int expireOverdue();

    /**
     * Единственное место, где считается «доступно = остаток − брони».
     * exceptReservationId — продаваемая бронь: её резерв не вычитается.
     */
    Map<UUID, Availability> availability(UUID warehouseId, Collection<UUID> productIds, @Nullable UUID exceptReservationId);

    /** Бронь должна быть ACTIVE и не просрочена, иначе исключение. */
    Reservation requireActiveForSale(UUID id);
}
