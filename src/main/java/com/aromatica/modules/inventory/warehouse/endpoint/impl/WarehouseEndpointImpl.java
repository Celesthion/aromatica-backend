package com.aromatica.modules.inventory.warehouse.endpoint.impl;

import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseDto;
import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseRequest;
import com.aromatica.modules.inventory.warehouse.endpoint.WarehouseEndpoint;
import com.aromatica.modules.inventory.warehouse.mapper.WarehouseMapper;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseEndpointImpl implements WarehouseEndpoint {

    WarehouseService warehouseService;
    WarehouseMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseDto> list() {
        return warehouseService.list().stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public WarehouseDto create(WarehouseRequest request) {
        return mapper.toDto(warehouseService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public WarehouseDto update(
            UUID id,
            WarehouseRequest request
    ) {
        return mapper.toDto(warehouseService.update(id, mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public WarehouseDto makeDefault(UUID id) {
        return mapper.toDto(warehouseService.makeDefault(id));
    }

    @Override
    @Transactional
    public WarehouseDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(warehouseService.setArchived(id, archived));
    }
}
