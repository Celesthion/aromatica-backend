package com.aromatica.modules.pos.reservation.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Сколько товара держат действующие брони склада. */
public interface ReservedQtyRow {
    UUID getProductId();
    BigDecimal getQty();
}
