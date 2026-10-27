package com.aromatica.modules.inventory.report.dtos;

import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockMovementDto;
import com.aromatica.modules.inventory.stock.model.StockLevel;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTO остатков и истории движений. */
public final class StockDtos {

    private StockDtos() {}

    /** Строка остатков: позиция, количество, себестоимость и уровень (в наличии / заканчивается / нет). */
    public record StockRow(
            UUID productId,
            String productName,
            BigDecimal qty,
            long avgCostMinor,
            long valueMinor,
            String kind,
            CatalogProduct.Unit unit,
            StockLevel level
    ) {}

    /** Сводка по складу для показателей над списками: считается в базе, без выгрузки всех строк. */
    public record StockSummary(
            long positions,
            long onHand,
            long low,
            long out,
            long totalValueMinor
    ) {}

    /** Движение позиции с реквизитами документа, которым оно сделано. */
    public record StockMovementDto(
            long id,
            UUID docId,
            DocType docType,
            long docNumber,
            DocStatus docStatus,
            BigDecimal qtyDelta,
            long costMinor,
            LocalDateTime moment
    ) {}
}
