package com.aromatica.modules.pos.reservation.model;

import com.aromatica.common.audit.Auditable;
import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Бронь товара за покупателем. Резерв мягкий: склад не меняется, касса вычитает брони из доступного;
 * просроченная перестаёт резервировать без смены статуса.
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation extends Auditable {

    /** EXPIRED ставит задача по расписанию; до неё просроченная бронь остаётся ACTIVE с прошедшим сроком. */
    public enum Status { ACTIVE, SOLD, CANCELLED, EXPIRED }

    @Id
    private UUID id;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column
    private String phone;

    /** null — бронь по имени и телефону, без карточки покупателя. */
    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Чек (SALE-документ), которым бронь продана. */
    @Column(name = "sold_doc_id")
    private UUID soldDocId;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<ReservationLine> lines = new ArrayList<>();

    /** Срок истёк: не резервирует, ждёт продления или отмены. */
    public boolean isExpired(LocalDateTime now) {
        return status == Status.EXPIRED || (status == Status.ACTIVE && !expiresAt.isAfter(now));
    }

    /** Не продана и не отменена: её можно продлить или отменить, а действующую — продать. */
    public boolean isOpen() {
        return status == Status.ACTIVE || status == Status.EXPIRED;
    }

    public boolean isReserving(LocalDateTime now) {
        return status == Status.ACTIVE && expiresAt.isAfter(now);
    }

    /** Отмена: просроченную тоже можно отменить, проданную и отменённую — нет. */
    public void cancel() {
        if (!isOpen()) {
            throw new ConflictException("Отменить можно только действующую или просроченную бронь");
        }
        status = Status.CANCELLED;
    }

    /**
     * Дни добавляются к текущему сроку, чтобы «+3 дн.» не укоротило бронь; у просроченной — от now.
     */
    public void extend(
            int days,
            LocalDateTime now
    ) {
        if (!isOpen()) {
            throw new ConflictException("Продлить можно только действующую или просроченную бронь");
        }
        expiresAt = (isExpired(now) ? now : expiresAt).plusDays(days);
        status = Status.ACTIVE;
    }

    /** Бронь можно продать, только пока она действует. */
    public void requireSellable(LocalDateTime now) {
        if (isExpired(now)) {
            throw new ConflictException("Бронь просрочена — продлите её перед продажей");
        }
        if (status != Status.ACTIVE) {
            throw new ConflictException("Бронь уже закрыта (продана или отменена)");
        }
    }

    /** Выкуп чеком. */
    public void markSold(UUID docId) {
        if (status != Status.ACTIVE) {
            throw new ConflictException("Бронь уже закрыта (продана или отменена)");
        }
        status = Status.SOLD;
        soldDocId = docId;
    }

    /** Сторно чека возвращает бронь клиенту; false — бронь не была продана, менять нечего. */
    public boolean reopenAfterVoid() {
        if (status != Status.SOLD) {
            return false;
        }
        status = Status.ACTIVE;
        soldDocId = null;
        return true;
    }

    /** Сколько бронь держит каждого товара прямо сейчас; истёкшая или закрытая не держит ничего. */
    public Map<UUID, BigDecimal> reservedQty(LocalDateTime now) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (isReserving(now)) {
            lines.forEach(l -> out.merge(l.getProductId(), l.getQty(), BigDecimal::add));
        }
        return out;
    }

    /** Цены, которые бронь пообещала клиенту, по товарам. */
    public Map<UUID, Long> heldPrices() {
        Map<UUID, Long> out = new HashMap<>();
        lines.forEach(l -> out.merge(l.getProductId(), l.getPriceMinor(), Math::min));
        return out;
    }

    /** Сумма брони по ценам строк. */
    public long sumMinor() {
        return lines.stream()
                .mapToLong(l -> CostMath.lineSumMinor(l.getQty(), l.getPriceMinor(), 0))
                .sum();
    }
}
