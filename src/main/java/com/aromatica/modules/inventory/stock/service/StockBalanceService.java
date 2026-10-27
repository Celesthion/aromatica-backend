package com.aromatica.modules.inventory.stock.service;

import com.aromatica.modules.inventory.stock.repository.StockBalanceRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Остатки склада для других модулей (касса, склады); чтение StockBalanceRepository только через него. */
public interface StockBalanceService {

    /** Нет строки баланса — товара нет в карте. */
    Map<UUID, BigDecimal> qtyMap(UUID warehouseId, Collection<UUID> productIds);

    /** До конца транзакции, в том же порядке, что проведение, — иначе дедлок с продажей или другой бронью. */
    void lockAll(UUID warehouseId, Collection<UUID> productIds);

    /** Ненулевой остаток, в том числе в минусе. */
    boolean hasStock(UUID warehouseId);

    List<UUID> positiveProductIds(UUID warehouseId);

}
