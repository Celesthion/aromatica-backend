package com.aromatica.modules.inventory.warehouse.service.impl;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import com.aromatica.modules.inventory.warehouse.repository.WarehouseRepository;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseLookupImpl implements WarehouseLookup {

    WarehouseRepository warehouseRepository;

    @Override
    public UUID shelfOf(UUID storeWarehouseId) {
        return warehouseRepository
                .findFirstByKindAndParentIdAndArchivedFalse(Warehouse.Kind.SHELF, storeWarehouseId)
                .orElseThrow(() -> new ConflictException(
                        "У склада нет полки — заведите её в справочнике складов"))
                .getId();
    }

    @Override
    public boolean isShelf(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId).map(Warehouse::isShelf).orElse(false);
    }

    @Override
    public UUID defaultWarehouseId() {
        return warehouseRepository.findFirstByIsDefaultTrue()
                .orElseThrow(() -> new ConflictException("Не найден склад по умолчанию"))
                .getId();
    }
}
