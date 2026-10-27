package com.aromatica.modules.pos.sale.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap.PricedLine;
import com.aromatica.modules.pos.sale.model.ReceiptLine;

import java.util.List;

/** Арифметика строк чека и возврата: сумма к оплате и платные строки. */
public final class ReceiptLines {

    private ReceiptLines() {
    }

    /** Оплата сходится с суммой строк; подарочные строки бесплатны и в сумму не входят. */
    public static long requirePayment(
            List<ReceiptLine> lines,
            long cashMinor,
            long noCashMinor
    ) {
        long total = lines.stream()
                .filter(l -> !l.gift())
                .mapToLong(l -> CostMath.lineSumMinor(l.qty(), l.priceMinor(), l.discountMinor()))
                .sum();
        long paid = cashMinor + noCashMinor;
        if (paid != total) {
            throw new BusinessRuleException("Оплата (" + paid
                    + ") не сходится с суммой чека (" + total + ")");
        }
        return total;
    }

    /** Строки чека — в строки документа: скидка уже суммой. */
    public static List<StockDocLine> toDocLines(List<ReceiptLine> lines) {
        return lines.stream().map(ReceiptLine::toDocLine).toList();
    }

    /** Платные строки для проверки потолка скидки: подарок бесплатен по правилу акции, не по скидке. */
    public static List<PricedLine> pricedLines(List<ReceiptLine> lines) {
        return lines.stream()
                .filter(l -> !l.gift())
                .map(l -> new PricedLine(l.productId(), l.qty(), l.priceMinor(), l.discountMinor()))
                .toList();
    }
}
