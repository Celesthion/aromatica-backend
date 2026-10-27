package com.aromatica.modules.masterdata.catalog.brand.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandDto;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandRequest;
import com.aromatica.modules.masterdata.catalog.brand.endpoint.BrandEndpoint;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSearchRequest;
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

@Tag(name = "Каталог: бренды")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrandController {

    BrandEndpoint brandEndpoint;

    @Operation(summary = "Поиск брендов")
    @PostMapping("/search")
    public PageResponse<BrandDto> search(@RequestBody BrandSearchRequest request) {
        return brandEndpoint.search(request);
    }

    @Operation(summary = "Создать бренд")
    @PostMapping
    public BrandDto create(@Valid @RequestBody BrandRequest request) {
        return brandEndpoint.create(request);
    }

    @Operation(summary = "Обновить бренд")
    @PutMapping("/{id}")
    public BrandDto update(
            @PathVariable UUID id,
            @Valid @RequestBody BrandRequest request
    ) {
        return brandEndpoint.update(id, request);
    }

    @Operation(summary = "Отправить бренд в архив")
    @PostMapping("/{id}/archive")
    public BrandDto archive(@PathVariable UUID id) {
        return brandEndpoint.setArchived(id, true);
    }

    @Operation(summary = "Вернуть бренд из архива")
    @PostMapping("/{id}/restore")
    public BrandDto restore(@PathVariable UUID id) {
        return brandEndpoint.setArchived(id, false);
    }
}
