package com.aromatica.modules.masterdata.catalog.product.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сборка имени товара из частей. У формулы есть SQL-зеркало
 * CatalogProductRepository.recomposeNames, оно пересчитывает имена при переименованиях.
 * Меняешь формулу здесь, поменяй и там: эти кейсы общие для обоих мест.
 */
class ProductNamingTest {

    @Test
    void fullName_allParts() {
        assertThat(ProductNaming.compose("Creed", "Aventus", "Флакон", null, new BigDecimal("100.000")))
                .isEqualTo("Creed — Aventus · Флакон · 100 мл");
    }

    @Test
    void label_betweenKindAndVolume() {
        assertThat(ProductNaming.compose("ПФ", "Кожаный", "Парфюмерный атомайзер", "Бежевый", new BigDecimal("10")))
                .isEqualTo("ПФ — Кожаный · Парфюмерный атомайзер · Бежевый · 10 мл");
    }

    @Test
    void withoutBrand_modelStandsAlone() {
        // у служебных товаров вроде упаковки бренда нет, и тире не рисуется
        assertThat(ProductNaming.compose(null, "Пакет", "Упаковка", null, null))
                .isEqualTo("Пакет · Упаковка");
        assertThat(ProductNaming.compose("  ", "Коробка", "Упаковка", "Премиальная", null))
                .isEqualTo("Коробка · Упаковка · Премиальная");
    }

    @Test
    void blankParts_skipped_andTrimmed() {
        assertThat(ProductNaming.compose(" Creed ", " Aventus ", " Флакон ", "  ", null))
                .isEqualTo("Creed — Aventus · Флакон");
    }

    @Test
    void volume_withoutTrailingZeros() {
        assertThat(ProductNaming.formatVolume(new BigDecimal("100.000"))).isEqualTo("100");
        assertThat(ProductNaming.formatVolume(new BigDecimal("0.500"))).isEqualTo("0.5");
        assertThat(ProductNaming.formatVolume(new BigDecimal("12.30"))).isEqualTo("12.3");
    }
}
