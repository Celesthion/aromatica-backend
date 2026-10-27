package com.aromatica.modules.pos.reservation.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap.PricedLine;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.model.ReservationLine;
import com.aromatica.modules.pos.reservation.model.ReservedQtyRow;
import com.aromatica.modules.pos.reservation.repository.ReservationRepository;
import com.aromatica.modules.pos.reservation.service.ReservationService.Availability;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReservationServiceImpl implements ReservationService {

    ReservationRepository reservationRepository;
    StockBalanceService stockBalanceService;
    SettingService settingService;
    ProductService productService;
    CounterpartyService counterpartyService;
    SellerDiscountCap discountCap;
    UserDirectory users;
    Clock clock;

    @Transactional
    @Override
    public Reservation create(
            Reservation reservation,
            Integer days
    ) {
        UUID warehouseId = reservation.getWarehouseId();
        List<ReservationLine> lines = new ArrayList<>(reservation.getLines());
        // копия: бронь показывает то, что назвали клиенту, даже если карточку переименуют
        requireWarehouseAccess(warehouseId);
        Counterparty customer = counterpartyService.find(reservation.getCounterpartyId());
        // блокировка остатков в порядке проведения: иначе две брони (или бронь и продажа)
        // увидят один и тот же свободный остаток
        stockBalanceService.lockAll(warehouseId, lines.stream().map(ReservationLine::getProductId).toList());
        requireAvailability(warehouseId, lines);
        // цену брони касса потом продаст как обещанную, поэтому потолок скидки — уже здесь
        discountCap.require(lines.stream()
                .map(l -> new PricedLine(l.getProductId(), l.getQty(), l.getPriceMinor(), 0))
                .toList(), Map.of());

        reservation.setId(UUID.randomUUID());
        reservation.setCustomerName(customer.getName().trim());
        reservation.setPhone(customer.getPhone());
        reservation.setCounterpartyId(customer.getId());
        reservation.setStatus(Reservation.Status.ACTIVE);
        reservation.setExpiresAt(LocalDateTime.now(clock).plusDays(effectiveDays(days)));
        reservation.getLines().clear();
        int lineNo = 1;
        for (ReservationLine line : lines) {
            line.setId(UUID.randomUUID());
            line.setReservation(reservation);
            line.setLineNo(lineNo++);
            reservation.getLines().add(line);
        }
        return reservationRepository.save(reservation);
    }

    /** Брони склада видит и меняет только тот, кто на этом складе работает (и админ). */
    private void requireWarehouseAccess(UUID warehouseId) {
        if (!users.require(CurrentUser.login()).canAccessWarehouse(warehouseId)) {
            throw new AccessDeniedException("Нет доступа к броням этого склада — обратитесь к администратору");
        }
    }

    /** null — default_days из настроек; всё выше max_days срезается. Общее для создания и продления. */
    private int effectiveDays(Integer days) {
        int max = settingService.getInt(SettingService.RESERVATION_MAX_DAYS, 14);
        int effective = days != null
                ? days
                : settingService.getInt(SettingService.RESERVATION_DEFAULT_DAYS, 3);
        if (effective < 1) {
            throw new BusinessRuleException("Срок брони — минимум 1 день");
        }
        return Math.min(effective, max);
    }

    /** Новой брони должно хватать остатка за вычетом других активных броней. */
    private void requireAvailability(
            UUID warehouseId,
            List<ReservationLine> lines
    ) {
        Map<UUID, BigDecimal> needed = new HashMap<>();
        lines.forEach(l -> needed.merge(l.getProductId(), l.getQty(), BigDecimal::add));
        Map<UUID, Availability> availability = availability(warehouseId, needed.keySet(), null);
        for (var e : needed.entrySet()) {
            BigDecimal available = availability.get(e.getKey()).available();
            if (available.compareTo(e.getValue()) < 0) {
                throw new ConflictException("«" + productService.nameOf(e.getKey())
                        + "»: доступно " + available.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                        + " (с учётом других броней), нужно "
                        + e.getValue().stripTrailingZeros().toPlainString());
            }
        }
    }

    @Transactional
    @Override
    public void cancel(UUID id) {
        Reservation reservation = require(id);
        requireWarehouseAccess(reservation.getWarehouseId());
        reservation.cancel();
        reservationRepository.save(reservation);
    }

    @Transactional
    @Override
    public Reservation extend(
            UUID id,
            Integer days
    ) {
        Reservation reservation = require(id);
        requireWarehouseAccess(reservation.getWarehouseId());
        reservation.extend(effectiveDays(days), LocalDateTime.now(clock));
        return reservationRepository.save(reservation);
    }

    @Transactional
    @Override
    public void markSold(
            UUID id,
            UUID docId
    ) {
        Reservation reservation = require(id);
        reservation.markSold(docId);
        reservationRepository.save(reservation);
    }

    @Transactional
    @Override
    public void restoreAfterVoid(UUID docId) {
        // сторно вернуло товар и деньги — бронь тоже возвращается клиенту
        reservationRepository.findBySoldDocId(docId).ifPresent(reservation -> {
            if (reservation.reopenAfterVoid()) {
                reservationRepository.save(reservation);
            }
        });
    }

    @Override
    public List<Reservation> list(UUID warehouseId) {
        requireWarehouseAccess(warehouseId);
        return reservationRepository.findByWarehouseIdAndStatusInOrderByCreatedAtDesc(
                warehouseId, List.of(Reservation.Status.ACTIVE, Reservation.Status.EXPIRED));
    }

    @Transactional
    @Override
    public int expireOverdue() {
        return reservationRepository.expireOverdue(
                Reservation.Status.ACTIVE, Reservation.Status.EXPIRED, LocalDateTime.now(clock));
    }

    @Override
    public Map<UUID, Availability> availability(
            UUID warehouseId,
            Collection<UUID> productIds,
            @Nullable UUID exceptReservationId
    ) {
        Map<UUID, Availability> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        Set<UUID> ids = Set.copyOf(productIds);
        Map<UUID, BigDecimal> stock = stockBalanceService.qtyMap(warehouseId, ids);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ReservedQtyRow> rows = exceptReservationId == null
                ? reservationRepository.sumReserved(warehouseId, ids, Reservation.Status.ACTIVE, now)
                : reservationRepository.sumReservedExcept(warehouseId, ids, Reservation.Status.ACTIVE, now,
                        exceptReservationId);
        Map<UUID, BigDecimal> reserved = new HashMap<>();
        rows.forEach(row -> reserved.put(row.getProductId(), row.getQty()));
        ids.forEach(id -> out.put(id, new Availability(
                stock.getOrDefault(id, BigDecimal.ZERO), reserved.getOrDefault(id, BigDecimal.ZERO))));
        return out;
    }

    @Transactional
    @Override
    public Reservation requireActiveForSale(UUID id) {
        // блокировка строки: продажа идёт в одной транзакции с чеком, второй
        // параллельный чек подождёт и увидит бронь уже проданной
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Бронь не найдена"));
        reservation.requireSellable(LocalDateTime.now(clock));
        return reservation;
    }

    private Reservation require(UUID id) {
        return reservationRepository.require(id, "Бронь не найдена");
    }
}
