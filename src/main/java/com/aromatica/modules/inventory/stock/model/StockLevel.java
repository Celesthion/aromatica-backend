package com.aromatica.modules.inventory.stock.model;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;

import java.math.BigDecimal;

/** Уровень остатка позиции: по нему подсвечивают строки и считают «заканчивается». */
public enum StockLevel {
    IN_STOCK,
    LOW,
    OUT;

    /** Порог «заканчивается» для наливного товара, мл. */
    public static final BigDecimal LOW_ML = new BigDecimal("10");
    /** Порог «заканчивается» для штучного товара, шт. */
    public static final BigDecimal LOW_PCS = new BigDecimal("5");

    public static BigDecimal lowThreshold(CatalogProduct.Unit unit) {
        return unit == CatalogProduct.Unit.ML ? LOW_ML : LOW_PCS;
    }

    public static StockLevel of(
            BigDecimal qty,
            CatalogProduct.Unit unit
    ) {
        if (qty.signum() <= 0) {
            return OUT;
        }
        return qty.compareTo(lowThreshold(unit)) <= 0 ? LOW : IN_STOCK;
    }
}
