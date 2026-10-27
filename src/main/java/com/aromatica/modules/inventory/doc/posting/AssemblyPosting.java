package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Сборка: составляющие уходят со склада документа, вещь приходует на склад-приёмник с суммарной себестоимостью. */
@Component
public class AssemblyPosting extends CompositionPosting {

    @Override
    public DocType type() {
        return DocType.ASSEMBLY;
    }

    @Override
    protected UUID thingWarehouse(StockDoc doc) {
        return warehouseTo(doc);
    }

    @Override
    protected UUID partsWarehouse(StockDoc doc) {
        return doc.getWarehouseId();
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        StockDocLine target = compositeLine(doc, ctx);
        UUID from = partsWarehouse(doc);
        UUID to = thingWarehouse(doc);
        requireLinesMatchComposition(doc, target, ctx);

        long totalCost = 0;
        for (StockDocLine line : doc.getLines()) {
            if (line == target) {
                continue;
            }
            long avg = ctx.balance(line.getProductId(), from)
                    .issue(line.getQty(), "сборки («" + ctx.productName(line.getProductId()) + "»)");
            ctx.writeMovement(doc, line, line.getQty().negate(), avg, from);
            line.setCostMinor(avg);
            totalCost += CostMath.costSumMinor(line.getQty(), avg);
        }
        long unitCost = PostingContext.perUnit(totalCost, target.getQty());
        ctx.writeMovement(doc, target, target.getQty(), unitCost, to);
        ctx.balance(target.getProductId(), to).receive(target.getQty(), unitCost);
        target.setCostMinor(unitCost);
        return totalCost;
    }
}
