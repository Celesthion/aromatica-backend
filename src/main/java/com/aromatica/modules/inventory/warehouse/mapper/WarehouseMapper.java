package com.aromatica.modules.inventory.warehouse.mapper;

import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseDto;
import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseRequest;
import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper
public interface WarehouseMapper {

    // Геттер isDefault() → property "default"; целевой компонент записи — "isDefault".
    @Mapping(target = "isDefault", source = "default")
    WarehouseDto toDto(Warehouse warehouse);

    /** Только то, что задаёт пользователь; вид, родитель, «по умолчанию», архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    Warehouse toEntity(WarehouseRequest request);

}
