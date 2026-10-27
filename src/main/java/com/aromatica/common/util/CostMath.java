package com.aromatica.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Арифметика проведения в тыйынах, HALF_UP. Скидка строки хранится суммой, а не процентом:
 * сумма строки остаётся целой без округлений, процент — только для ввода и показа.
 */
public final class CostMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CostMath() {
    }

    /** qty × цена без скидки, HALF_UP. */
    public static long grossMinor(
            BigDecimal qty,
            long priceMinor
    ) {
        return qty.multiply(BigDecimal.valueOf(priceMinor))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * qty × цена − скидка, не ниже нуля. SQL-зеркало — {@code StockDocRepository.salesGroups}:
     * менять синхронно.
     */
    public static long lineSumMinor(
            BigDecimal qty,
            long priceMinor,
            long discountMinor
    ) {
        return Math.max(0, grossMinor(qty, priceMinor) - discountMinor);
    }

    /** Цена единицы с учётом скидки, HALF_UP; при нулевом количестве — цена как есть. */
    public static long unitPriceMinor(
            BigDecimal qty,
            long priceMinor,
            long discountMinor
    ) {
        if (qty == null || qty.signum() == 0) {
            return priceMinor;
        }
        return BigDecimal.valueOf(lineSumMinor(qty, priceMinor, discountMinor))
                .divide(qty, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Скидка суммой из процента, HALF_UP. */
    public static long discountMinorForPct(
            BigDecimal qty,
            long priceMinor,
            BigDecimal discountPct
    ) {
        if (discountPct == null || discountPct.signum() == 0) {
            return 0;
        }
        return qty.multiply(BigDecimal.valueOf(priceMinor))
                .multiply(discountPct)
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Процент скидки для показа (2 знака). В расчётах не участвует. */
    public static BigDecimal discountPctFor(
            BigDecimal qty,
            long priceMinor,
            long discountMinor
    ) {
        long gross = grossMinor(qty, priceMinor);
        if (discountMinor <= 0 || gross <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(discountMinor)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(gross), 2, RoundingMode.HALF_UP);
    }

    /**
     * Скользящая средняя при приходе; если остаток был ≤ 0, сбрасывается в себестоимость прихода.
     * SQL-зеркало — {@code StockMovementRepository.foldBalance}: менять синхронно.
     */
    public static long newAvgCostMinor(
            BigDecimal oldQty,
            long oldAvgMinor,
            BigDecimal inQty,
            long inCostMinor
    ) {
        if (oldQty == null || oldQty.signum() <= 0) {
            return inCostMinor;
        }
        BigDecimal totalQty = oldQty.add(inQty);
        if (totalQty.signum() == 0) {
            return inCostMinor;
        }
        BigDecimal totalValue = oldQty.multiply(BigDecimal.valueOf(oldAvgMinor))
                .add(inQty.multiply(BigDecimal.valueOf(inCostMinor)));
        return totalValue.divide(totalQty, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** qty × себестоимость единицы, HALF_UP. */
    public static long costSumMinor(
            BigDecimal qty,
            long costMinor
    ) {
        return grossMinor(qty, costMinor);
    }
}
