package com.aromatica.modules.inventory.stock.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Составной ключ кэша остатков: товар + склад. */
public class StockBalanceId implements Serializable {

    private UUID productId;
    private UUID warehouseId;

    public StockBalanceId() {
    }

    public StockBalanceId(
            UUID productId,
            UUID warehouseId
    ) {
        this.productId = productId;
        this.warehouseId = warehouseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockBalanceId that)) return false;
        return Objects.equals(productId, that.productId)
                && Objects.equals(warehouseId, that.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, warehouseId);
    }
}
