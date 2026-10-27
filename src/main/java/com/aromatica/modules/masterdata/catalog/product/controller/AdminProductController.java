package com.aromatica.modules.masterdata.catalog.product.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductDto;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductRequest;
import com.aromatica.modules.masterdata.catalog.product.endpoint.ProductEndpoint;
import com.aromatica.modules.masterdata.catalog.product.search.ProductSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Каталог: товары")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminProductController {

    ProductEndpoint productEndpoint;

    @Operation(summary = "Поиск товаров")
    @PostMapping("/search")
    public PageResponse<ProductDto> search(@RequestBody ProductSearchRequest request) {
        return productEndpoint.search(request);
    }

    @Operation(summary = "Создать товар")
    @PostMapping
    public ProductDto create(@Valid @RequestBody ProductRequest request) {
        return productEndpoint.create(request);
    }

    @Operation(summary = "Обновить товар")
    @PutMapping("/{id}")
    public ProductDto update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        return productEndpoint.update(id, request);
    }

    @Operation(
            summary = "Отправить товар в архив",
            description = "Удалить товар нельзя, только архивировать: на него ссылаются документы и движения"
    )
    @PostMapping("/{id}/archive")
    public ProductDto archive(@PathVariable UUID id) {
        return productEndpoint.setArchived(id, true);
    }

    @Operation(
            summary = "Вернуть товар из архива",
            description = "По тем же правилам, что и создание: без дубля и без второй активной парфюмерной воды"
    )
    @PostMapping("/{id}/restore")
    public ProductDto restore(@PathVariable UUID id) {
        return productEndpoint.setArchived(id, false);
    }
}
