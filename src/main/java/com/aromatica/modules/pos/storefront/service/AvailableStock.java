package com.aromatica.modules.pos.storefront.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Сколько товара можно продать: остаток склада минус брони. Витрине нужно только число,
 * поэтому о бронях она не знает — реализует интерфейс модуль броней.
 */
public interface AvailableStock {

    /** productId → доступно; без обрезки до нуля: ушедший в минус остаток кассир видит как есть. */
    Map<UUID, BigDecimal> availableQty(UUID warehouseId, Collection<UUID> productIds);
}
