package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Приёмка: +qty по закупочной цене со скидкой, пересчёт скользящей средней. */
@Component
public class SupplyPosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.SUPPLY;
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        long unitCost = CostMath.unitPriceMinor(line.getQty(), line.getPriceMinor(), line.getDiscountMinor());
        ctx.writeMovement(doc, line, line.getQty(), unitCost, warehouse);
        ctx.balance(line.getProductId(), warehouse).receive(line.getQty(), unitCost);
        line.setCostMinor(unitCost);
        return line.sumMinor();
    }
}
