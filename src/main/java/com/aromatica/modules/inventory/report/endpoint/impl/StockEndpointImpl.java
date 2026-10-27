package com.aromatica.modules.inventory.report.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockMovementDto;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockSummary;
import com.aromatica.modules.inventory.report.endpoint.StockEndpoint;
import com.aromatica.modules.inventory.report.mapper.StockHistoryMapper;
import com.aromatica.modules.inventory.report.search.StockSearchRequest;
import com.aromatica.modules.inventory.report.service.StockQueryService;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import com.aromatica.modules.inventory.stock.service.StockLedger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockEndpointImpl implements StockEndpoint {

    StockQueryService stockQueryService;
    /** Движения позиции — из журнала остатков, реквизиты документов добавляет маппер. */
    StockLedger ledger;
    StockHistoryMapper historyMapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockRow> search(StockSearchRequest request) {
        return stockQueryService.search(request);
    }

    @Override
    @Transactional(readOnly = true)
    public StockSummary summary(UUID warehouseId) {
        return stockQueryService.summary(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public SliceResponse<StockMovementDto> history(
            UUID productId,
            UUID warehouseId,
            int page,
            int size
    ) {
        Slice<StockMovement> movements = ledger.movements(productId, warehouseId, page, size);
        return pageMapper.toSlice(movements, historyMapper.toDtos(movements.getContent()));
    }
}
