package com.aromatica.modules.inventory.stock;

import com.aromatica.modules.inventory.stock.model.StockLevel;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Уровень остатка: порог «заканчивается» — 10 мл для наливного, 5 шт для штучного. */
class StockLevelTest {

    @Test
    void of_pieceGoods() {
        assertThat(StockLevel.of(BigDecimal.ZERO, CatalogProduct.Unit.PCS)).isEqualTo(StockLevel.OUT);
        assertThat(StockLevel.of(new BigDecimal("-1"), CatalogProduct.Unit.PCS)).isEqualTo(StockLevel.OUT);
        assertThat(StockLevel.of(new BigDecimal("5"), CatalogProduct.Unit.PCS)).isEqualTo(StockLevel.LOW);
        assertThat(StockLevel.of(new BigDecimal("6"), CatalogProduct.Unit.PCS)).isEqualTo(StockLevel.IN_STOCK);
    }

    @Test
    void of_pouredGoods() {
        assertThat(StockLevel.of(new BigDecimal("10"), CatalogProduct.Unit.ML)).isEqualTo(StockLevel.LOW);
        assertThat(StockLevel.of(new BigDecimal("10.5"), CatalogProduct.Unit.ML)).isEqualTo(StockLevel.IN_STOCK);
    }
}
