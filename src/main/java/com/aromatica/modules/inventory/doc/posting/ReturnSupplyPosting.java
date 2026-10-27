package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Возврат поставщику: −qty по средней; итог — деньги от поставщика (по цене строки,
 * а при нулевой цене — по себестоимости).
 */
@Component
public class ReturnSupplyPosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.RETURN_SUPPLY;
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        long cost = ctx.balance(line.getProductId(), warehouse).issue(line.getQty(), "возврата поставщику");
        ctx.writeMovement(doc, line, line.getQty().negate(), cost, warehouse);
        line.setCostMinor(cost);
        return line.getPriceMinor() > 0
                ? line.sumMinor()
                : CostMath.costSumMinor(line.getQty(), cost);
    }
}
