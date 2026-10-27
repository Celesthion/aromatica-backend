package com.aromatica.modules.inventory.report.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockMovementDto;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockSummary;
import com.aromatica.modules.inventory.report.endpoint.StockEndpoint;
import com.aromatica.modules.inventory.report.search.StockSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Склад: остатки", description = "Остатки товаров и история их движений.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER')")
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockController {

    StockEndpoint stockEndpoint;

    @Operation(
            summary = "Поиск остатков",
            description = "Страницы, сортировка и фильтр по названию или списку позиций; склад по умолчанию — основной"
    )
    @PostMapping("/search")
    public PageResponse<StockRow> search(@RequestBody StockSearchRequest request) {
        return stockEndpoint.search(request);
    }

    @Operation(
            summary = "Сводка по складу",
            description = "Позиций, в наличии, заканчивается (до 10 мл или 5 шт), нет в наличии и стоимость склада"
    )
    @GetMapping("/summary")
    public StockSummary summary(@RequestParam(required = false) UUID warehouseId) {
        return stockEndpoint.summary(warehouseId);
    }

    @Operation(summary = "История движений товара")
    @GetMapping("/{productId}/history")
    public SliceResponse<StockMovementDto> history(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockEndpoint.history(productId, warehouseId, page, size);
    }
}
