package com.aromatica.modules.inventory.warehouse.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO складов. */
public final class WarehouseDtos {

    private WarehouseDtos() {}

    public record WarehouseDto(

            UUID id,

            String name,

            String address,

            boolean isDefault,

            boolean archived,

            /** STORE — склад магазина, SHELF — его полка. */
            String kind,

            UUID parentId
    ) {}

    public record WarehouseRequest(

            @NotBlank(message = "Не указано название склада")
            @Size(max = 255)
            String name,

            @Size(max = 500)
            String address
    ) {}
}
