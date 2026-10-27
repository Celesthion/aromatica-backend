package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Продажа: −qty по текущей средней, она фиксируется в строке; в минус не уводим. Комплект на
 * складе магазина существует только виртуально: продажа разворачивается в его составляющие.
 */
@Component
public class SalePosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.SALE;
    }

    @Override
    public boolean allowsCompositeOffShelf(
            StockDocLine line,
            PostingContext ctx
    ) {
        return ctx.isKit(line.getProductId());
    }

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        for (StockDocLine line : doc.getLines()) {
            UUID warehouse = PostingContext.lineWarehouse(doc, line);
            if (isVirtualKit(line, warehouse, ctx)) {
                ctx.componentsOf(line.getProductId())
                        .forEach(c -> keys.add(new BalanceKey(c.getComponentId(), warehouse)));
            } else {
                keys.add(new BalanceKey(line.getProductId(), warehouse));
            }
        }
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        if (isVirtualKit(line, warehouse, ctx)) {
            return postKit(doc, line, warehouse, ctx);
        }
        // касса отбивает минус раньше, но и документ из админки не должен уводить остаток ниже нуля
        long cost = ctx.balance(line.getProductId(), warehouse).issue(line.getQty(), "продажи");
        ctx.writeMovement(doc, line, line.getQty().negate(), cost, warehouse);
        line.setCostMinor(cost);
        return line.sumMinor();
    }

    @Override
    public void settle(StockDoc doc) {
        ReceiptPayment.settle(doc);
    }

    private static boolean isVirtualKit(
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        return ctx.isKit(line.getProductId()) && !ctx.isShelf(warehouse);
    }

    /** Движения по составляющим ссылаются на строку комплекта — в истории видно, каким чеком ушла коробка. */
    private static long postKit(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        long totalCost = 0;
        for (ProductComponent c : ctx.componentsOf(line.getProductId())) {
            BigDecimal qty = c.getQty().multiply(line.getQty());
            long avg = ctx.balance(c.getComponentId(), warehouse)
                    .issue(qty, "продажи комплекта («" + ctx.productName(c.getComponentId()) + "»)");
            ctx.writeMovement(doc, line, c.getComponentId(), qty.negate(), avg, warehouse);
            totalCost += CostMath.costSumMinor(qty, avg);
        }
        line.setCostMinor(PostingContext.perUnit(totalCost, line.getQty()));
        return line.sumMinor();
    }
}
