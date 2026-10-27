package com.aromatica.modules.inventory.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Запись журнала движений: неизменяема, удаляется только распроведением. Остаток = сумма qtyDelta. */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    /** Выдаётся после блокировок (StockLedger.record): порядок id — порядок проведения. */
    @Id
    private Long id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "qty_delta", nullable = false, precision = 14, scale = 3)
    private BigDecimal qtyDelta;

    @Column(name = "cost_minor", nullable = false)
    private long costMinor;

    @Column(nullable = false)
    private LocalDateTime moment;
}
