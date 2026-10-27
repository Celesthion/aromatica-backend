package com.aromatica.modules.inventory.report.service;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockSummary;
import com.aromatica.modules.inventory.report.search.StockSearchRequest;

import java.util.UUID;

/** Чтение остатков для админки: строка — проекция баланса и товара, собирается здесь. */
public interface StockQueryService {

    /** Фильтр, сортировка и страница — в базе. */
    PageResponse<StockRow> search(StockSearchRequest request);

    StockSummary summary(UUID warehouse);

}
