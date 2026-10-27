package com.aromatica.modules.inventory.warehouse.service;

import java.util.UUID;

/** Склад по умолчанию и полки складов магазина — одно место для этих правил. */
public interface WarehouseLookup {

    UUID defaultWarehouseId();

    /** Полка склада магазина: сюда уходит то, что на обычный склад не возвращается. */
    UUID shelfOf(UUID storeWarehouseId);

    boolean isShelf(UUID warehouseId);
}
