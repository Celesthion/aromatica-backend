package com.aromatica.modules.inventory.warehouse.controller;

import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseDto;
import com.aromatica.modules.inventory.warehouse.dtos.WarehouseDtos.WarehouseRequest;
import com.aromatica.modules.inventory.warehouse.endpoint.WarehouseEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Склад: склады", description = "Справочник складов.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER')")
@RequestMapping("/api/admin/warehouses")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseController {

    WarehouseEndpoint warehouseEndpoint;

    @Operation(summary = "Список складов")
    @GetMapping
    public List<WarehouseDto> list() {
        return warehouseEndpoint.list();
    }

    @Operation(summary = "Создать склад")
    @PostMapping
    public WarehouseDto create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseEndpoint.create(request);
    }

    @Operation(summary = "Изменить склад")
    @PutMapping("/{id}")
    public WarehouseDto update(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseRequest request
    ) {
        return warehouseEndpoint.update(id, request);
    }

    @Operation(summary = "Сделать складом по умолчанию", description = "Склад по умолчанию подставляется в новые документы.")
    @PostMapping("/{id}/default")
    public WarehouseDto makeDefault(@PathVariable UUID id) {
        return warehouseEndpoint.makeDefault(id);
    }

    @Operation(
            summary = "Отправить склад в архив",
            description = "Склад скрывается из списков; движения и история сохраняются. Склад по умолчанию и склад с остатками архивировать нельзя"
    )
    @PostMapping("/{id}/archive")
    public WarehouseDto archive(@PathVariable UUID id) {
        return warehouseEndpoint.setArchived(id, true);
    }

    @Operation(summary = "Вернуть склад из архива")
    @PostMapping("/{id}/restore")
    public WarehouseDto restore(@PathVariable UUID id) {
        return warehouseEndpoint.setArchived(id, false);
    }
}
