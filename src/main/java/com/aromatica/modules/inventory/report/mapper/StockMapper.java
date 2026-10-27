package com.aromatica.modules.inventory.report.mapper;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockLevel;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import org.mapstruct.Mapper;

/** Остатки склада → DTO. */
@Mapper
public interface StockMapper {

    /** Строка остатков; стоимость и уровень считаются из количества и средней себестоимости. */
    default StockRow toRow(StockBalance balance, String productName, String kind, CatalogProduct.Unit unit) {
        return new StockRow(balance.getProductId(), productName, balance.getQty(), balance.getAvgCostMinor(),
                CostMath.costSumMinor(balance.getQty(), balance.getAvgCostMinor()), kind, unit,
                StockLevel.of(balance.getQty(), unit));
    }
}
