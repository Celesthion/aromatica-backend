package com.aromatica.modules.inventory.warehouse.endpoint;

import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseDto;
import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseRequest;

import java.util.List;
import java.util.UUID;

/** Справочник складов: DTO-слой. */
public interface WarehouseEndpoint {

    List<WarehouseDto> list();

    WarehouseDto create(WarehouseRequest request);

    WarehouseDto update(UUID id, WarehouseRequest request);

    WarehouseDto makeDefault(UUID id);

    WarehouseDto setArchived(UUID id, boolean archived);
}
