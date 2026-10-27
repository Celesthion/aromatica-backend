package com.aromatica.modules.inventory.report.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockMovementDto;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockSummary;
import com.aromatica.modules.inventory.report.search.StockSearchRequest;

import java.util.UUID;

/** Остатки и история движений: DTO-слой. */
public interface StockEndpoint {

    PageResponse<StockRow> search(StockSearchRequest request);

    StockSummary summary(UUID warehouseId);

    SliceResponse<StockMovementDto> history(UUID productId, UUID warehouseId, int page, int size);
}
