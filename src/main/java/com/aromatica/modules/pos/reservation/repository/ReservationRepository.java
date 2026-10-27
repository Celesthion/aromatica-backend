package com.aromatica.modules.pos.reservation.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.model.ReservedQtyRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends EntityRepository<Reservation, UUID> {

    List<Reservation> findByWarehouseIdAndStatusInOrderByCreatedAtDesc(UUID warehouseId, Collection<Reservation.Status> statuses);

    /**
     * Массовое обновление идёт мимо сущностей: версию и время правки ставим сами,
     * иначе чужая открытая правка о нём не узнает.
     */
    @Modifying
    @Query("""
            update Reservation r set r.status = :expired, r.version = r.version + 1, r.updatedAt = :now
            where r.status = :active and r.expiresAt <= :now""")
    int expireOverdue(
            @Param("active") Reservation.Status active,
            @Param("expired") Reservation.Status expired,
            @Param("now") LocalDateTime now);

    /**
     * Резерв действующих броней склада; просроченная бронь в статусе ACTIVE ничего не держит.
     */
    @Query("""
            select l.productId as productId, sum(l.qty) as qty from ReservationLine l join l.reservation r
            where r.warehouseId = :warehouseId and r.status = :active and r.expiresAt > :now
              and l.productId in :productIds
            group by l.productId""")
    List<ReservedQtyRow> sumReserved(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") Collection<UUID> productIds,
            @Param("active") Reservation.Status active,
            @Param("now") LocalDateTime now);

    /** То же без одной брони: свой резерв продаже самой брони не мешает. */
    @Query("""
            select l.productId as productId, sum(l.qty) as qty from ReservationLine l join l.reservation r
            where r.warehouseId = :warehouseId and r.status = :active and r.expiresAt > :now
              and l.productId in :productIds and r.id <> :exceptId
            group by l.productId""")
    List<ReservedQtyRow> sumReservedExcept(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") Collection<UUID> productIds,
            @Param("active") Reservation.Status active,
            @Param("now") LocalDateTime now,
            @Param("exceptId") UUID exceptId);

    /**
     * Блокировка для продажи: иначе двойной клик выкупит бронь дважды, и остаток уйдёт в минус.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(UUID id);

    /** Нужно при сторно, чтобы вернуть бронь, которую закрыл чек. */
    Optional<Reservation> findBySoldDocId(UUID soldDocId);
}
