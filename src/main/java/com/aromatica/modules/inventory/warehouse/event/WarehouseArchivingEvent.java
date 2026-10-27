package com.aromatica.modules.inventory.warehouse.event;

import com.aromatica.modules.inventory.warehouse.model.Warehouse;

/**
 * Склад вот-вот уйдёт в архив. Слушатели проверяют, можно ли: исключение в слушателе
 * отменяет архивацию. Так склад не знает, у кого на него есть данные.
 */
public record WarehouseArchivingEvent(
        Warehouse warehouse
) {
}
