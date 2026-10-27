package com.aromatica.modules.inventory.stock.model;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Кэш остатков — производная от журнала stock_movement; пишет только StockLedger, в транзакции с движениями. */
@Entity
@Table(name = "stock_balance")
@IdClass(StockBalanceId.class)
@Getter
@Setter
@NoArgsConstructor
public class StockBalance {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Id
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty = BigDecimal.ZERO;

    /** Скользящая средняя; та же формула в SQL StockMovementRepository.foldBalance — менять синхронно. */
    @Column(name = "avg_cost_minor", nullable = false)
    private long avgCostMinor;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public StockBalance(
            UUID productId,
            UUID warehouseId
    ) {
        this.productId = productId;
        this.warehouseId = warehouseId;
    }

    public void receive(
            BigDecimal inQty,
            long unitCostMinor
    ) {
        avgCostMinor = CostMath.newAvgCostMinor(qty, avgCostMinor, inQty, unitCostMinor);
        qty = qty.add(inQty);
    }

    /** Расход по средней, в минус нельзя; operation — для текста отказа. Возвращает себестоимость единицы. */
    public long issue(
            BigDecimal outQty,
            String operation
    ) {
        if (qty.compareTo(outQty) < 0) {
            throw new ConflictException("Недостаточно остатка для " + operation
                    + ": есть " + qty.stripTrailingZeros().toPlainString()
                    + ", нужно " + outQty.stripTrailingZeros().toPlainString());
        }
        qty = qty.subtract(outQty);
        return avgCostMinor;
    }

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
