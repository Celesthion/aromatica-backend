package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Списание: −qty по средней. В минус нельзя — сначала оприходуйте излишек или проведите инвентаризацию. */
@Component
public class WriteOffPosting extends LinePosting {

    @Override
    public DocType type() {
        return DocType.WRITE_OFF;
    }

    @Override
    protected long postLine(
            StockDoc doc,
            StockDocLine line,
            UUID warehouse,
            PostingContext ctx
    ) {
        long cost = ctx.balance(line.getProductId(), warehouse).issue(line.getQty(), "списания");
        ctx.writeMovement(doc, line, line.getQty().negate(), cost, warehouse);
        line.setCostMinor(cost);
        return CostMath.costSumMinor(line.getQty(), cost);
    }
}
