package com.aromatica.modules.pos.shift.model;

import com.aromatica.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Кассовая смена. Сводку считаем по чекам смены, здесь её не дублируем. */
@Entity
@Table(name = "pos_shift")
@Getter
@Setter
@NoArgsConstructor
public class PosShift {

    public enum Status { OPEN, CLOSED }

    @Id
    private UUID id;

    /** Сквозной, из sequence pos_shift_number. */
    @Column(nullable = false)
    private long number;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /** Размен в ящике на начало смены. */
    @Column(name = "opening_cash_minor", nullable = false)
    private long openingCashMinor;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Нал, пересчитанный при закрытии. */
    @Column(name = "counted_cash_minor")
    private Long countedCashMinor;

    /** Расчётный нал при закрытии: размен + продажи наличными − возвраты наличными. */
    @Column(name = "expected_cash_minor")
    private Long expectedCashMinor;

    @Column(columnDefinition = "text")
    private String comment;

    public static PosShift open(
            UUID id,
            long number,
            UUID warehouseId,
            UUID openedBy,
            LocalDateTime openedAt,
            long openingCashMinor
    ) {
        PosShift shift = new PosShift();
        shift.id = id;
        shift.number = number;
        shift.warehouseId = warehouseId;
        shift.status = Status.OPEN;
        shift.openedBy = openedBy;
        shift.openedAt = openedAt;
        shift.openingCashMinor = openingCashMinor;
        return shift;
    }

    public void requireOpen() {
        if (status != Status.OPEN) {
            throw new ConflictException("Смена №" + number + " уже закрыта");
        }
    }

    /**
     * Фиксирует расчётный нал: дальше чеки смены не меняются. countedCashMinor null — не пересчитывали.
     */
    public void close(
            UUID closedBy,
            LocalDateTime closedAt,
            long expectedCashMinor,
            Long countedCashMinor,
            String comment
    ) {
        requireOpen();
        this.status = Status.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.expectedCashMinor = expectedCashMinor;
        this.countedCashMinor = countedCashMinor;
        this.comment = comment;
    }
}
