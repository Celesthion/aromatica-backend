package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Оприходование излишка: +qty, себестоимость — цена строки, без цены — текущая средняя. */
@Component
public class SurplusPosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.SURPLUS;
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        StockBalance balance = ctx.balance(line.getProductId(), warehouse);
        long unitCost = line.getPriceMinor() > 0
                ? CostMath.unitPriceMinor(line.getQty(), line.getPriceMinor(), line.getDiscountMinor())
                : balance.getAvgCostMinor();
        ctx.writeMovement(doc, line, line.getQty(), unitCost, warehouse);
        balance.receive(line.getQty(), unitCost);
        line.setCostMinor(unitCost);
        return CostMath.costSumMinor(line.getQty(), unitCost);
    }
}
