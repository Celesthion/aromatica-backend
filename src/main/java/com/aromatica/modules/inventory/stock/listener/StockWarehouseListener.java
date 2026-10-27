package com.aromatica.modules.inventory.stock.listener;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.inventory.warehouse.event.WarehouseArchivingEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Склад с остатками в архив не уходит: товар сначала перемещают или списывают. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockWarehouseListener {

    StockBalanceService balanceService;

    @EventListener
    public void onArchiving(WarehouseArchivingEvent event) {
        if (balanceService.hasStock(event.warehouse().getId())) {
            throw new ConflictException("На складе есть остатки — сначала переместите или спишите товар");
        }
    }
}
