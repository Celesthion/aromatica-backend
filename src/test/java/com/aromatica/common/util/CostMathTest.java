package com.aromatica.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Арифметика проведения. Числа условные. */
class CostMathTest {

    @Test
    void gross_fractionalQty_roundsHalfUp() {
        // 2.5 мл × 120.00 = 300.00
        assertThat(CostMath.grossMinor(new BigDecimal("2.5"), 120_00)).isEqualTo(300_00);
        // 0.333 × 100 тыйын = 33.3 → 33
        assertThat(CostMath.grossMinor(new BigDecimal("0.333"), 100)).isEqualTo(33);
    }

    @Test
    void lineSum_isGrossMinusDiscount_neverNegative() {
        // 5 × 100.00 − 62.50 = 437.50, без всяких округлений
        assertThat(CostMath.lineSumMinor(new BigDecimal("5"), 100_00, 62_50)).isEqualTo(437_50);
        assertThat(CostMath.lineSumMinor(BigDecimal.ONE, 100_00, 0)).isEqualTo(100_00);
        assertThat(CostMath.lineSumMinor(BigDecimal.ONE, 100_00, 999_00)).isEqualTo(0);
    }

    @Test
    void unitPrice_afterDiscount_roundsHalfUp() {
        // 150.00 − 15.00 = 135.00 за штуку
        assertThat(CostMath.unitPriceMinor(BigDecimal.ONE, 150_00, 15_00)).isEqualTo(135_00);
        // 2 шт по 99 тыйын − 99 = 99 / 2 = 49.5 → 50 (HALF_UP)
        assertThat(CostMath.unitPriceMinor(BigDecimal.valueOf(2), 99, 99)).isEqualTo(50);
        // нулевое количество — цена как есть
        assertThat(CostMath.unitPriceMinor(BigDecimal.ZERO, 150_00, 10)).isEqualTo(150_00);
    }

    @Test
    void discountFromPct_andBack() {
        // 5 × 100.00 × 12.5% = 62.50
        assertThat(CostMath.discountMinorForPct(new BigDecimal("5"), 100_00, new BigDecimal("12.5")))
                .isEqualTo(62_50);
        assertThat(CostMath.discountMinorForPct(BigDecimal.ONE, 150_00, null)).isEqualTo(0);
        // 100 сомов от 38 000 — 0,26 % справочно (в расчётах процент не участвует)
        assertThat(CostMath.discountPctFor(BigDecimal.ONE, 38_000_00, 100_00))
                .isEqualByComparingTo("0.26");
        assertThat(CostMath.discountPctFor(BigDecimal.ONE, 0, 0)).isEqualByComparingTo("0");
    }

    @Test
    void newAvg_weightedAverage() {
        // 10 шт по 100.00 + 10 шт по 200.00 → средняя 150.00
        assertThat(CostMath.newAvgCostMinor(BigDecimal.TEN, 100_00, BigDecimal.TEN, 200_00))
                .isEqualTo(150_00);
        // 3 по 100.00 + 1 по 200.00 → (300+200)/4 = 125.00
        assertThat(CostMath.newAvgCostMinor(BigDecimal.valueOf(3), 100_00, BigDecimal.ONE, 200_00))
                .isEqualTo(125_00);
    }

    @Test
    void newAvg_resetsWhenStockWasZeroOrNegative() {
        assertThat(CostMath.newAvgCostMinor(BigDecimal.ZERO, 999_00, BigDecimal.TEN, 80_00))
                .isEqualTo(80_00);
        assertThat(CostMath.newAvgCostMinor(new BigDecimal("-5"), 999_00, BigDecimal.TEN, 80_00))
                .isEqualTo(80_00);
    }

    @Test
    void costSum_fractionalQty() {
        // 2.5 мл × 80.00 себестоимости = 200.00
        assertThat(CostMath.costSumMinor(new BigDecimal("2.5"), 80_00)).isEqualTo(200_00);
    }
}
