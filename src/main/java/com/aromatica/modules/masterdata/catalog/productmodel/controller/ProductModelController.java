package com.aromatica.modules.masterdata.catalog.productmodel.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ModelRow;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelDto;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.endpoint.ProductModelEndpoint;
import com.aromatica.modules.masterdata.catalog.productmodel.search.ProductModelSearchRequest;
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

import java.util.UUID;

@Tag(name = "Каталог: продукты", description = "Карточки продуктов, которые группируют товары-формы (SKU)")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
@RequestMapping("/api/admin/product-models")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelController {

    ProductModelEndpoint modelEndpoint;

    @Operation(summary = "Поиск продуктов")
    @PostMapping("/search")
    public PageResponse<ModelRow> search(@RequestBody ProductModelSearchRequest request) {
        return modelEndpoint.search(request);
    }

    @Operation(summary = "Карточка продукта", description = "Полная карточка продукта вместе с SKU")
    @GetMapping("/{id}")
    public ProductModelDto one(@PathVariable UUID id) {
        return modelEndpoint.one(id);
    }

    @Operation(summary = "Создать продукт")
    @PostMapping
    public ProductModelDto create(@Valid @RequestBody ProductModelRequest request) {
        return modelEndpoint.create(request);
    }

    @Operation(summary = "Обновить продукт")
    @PutMapping("/{id}")
    public ProductModelDto update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductModelRequest request
    ) {
        return modelEndpoint.update(id, request);
    }

    @Operation(summary = "Отправить продукт в архив")
    @PostMapping("/{id}/archive")
    public ProductModelDto archive(@PathVariable UUID id) {
        return modelEndpoint.setArchived(id, true);
    }

    @Operation(summary = "Вернуть продукт из архива")
    @PostMapping("/{id}/restore")
    public ProductModelDto restore(@PathVariable UUID id) {
        return modelEndpoint.setArchived(id, false);
    }
}
