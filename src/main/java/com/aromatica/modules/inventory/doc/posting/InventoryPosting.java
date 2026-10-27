package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Инвентаризация: строка — фактический остаток, движение — разница с учётным. */
@Component
public class InventoryPosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.INVENTORY;
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        StockBalance balance = ctx.balance(line.getProductId(), warehouse);
        BigDecimal delta = line.getQty().subtract(balance.getQty());
        long oldAvg = balance.getAvgCostMinor();
        long unitCost = oldAvg > 0 ? oldAvg
                : CostMath.unitPriceMinor(line.getQty(), line.getPriceMinor(), line.getDiscountMinor());
        line.setCostMinor(unitCost);
        if (delta.signum() == 0) {
            return 0;
        }
        ctx.writeMovement(doc, line, delta, unitCost, warehouse);
        if (delta.signum() > 0) {
            balance.receive(delta, unitCost);
        } else {
            balance.issue(delta.negate(), "инвентаризации");
        }
        return CostMath.costSumMinor(delta.abs(), unitCost);
    }
}
