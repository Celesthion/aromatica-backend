package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Перемещение: приход на приёмник по средней источника — стоимость едет вместе с товаром. */
@Component
public class MovePosting implements DocPosting {

    @Override
    public DocType type() {
        return DocType.MOVE;
    }

    @Override
    public void validate(
            StockDoc doc,
            PostingContext ctx
    ) {
        if (doc.getWarehouseToId() == null) {
            throw new ConflictException("У перемещения не указан склад-приёмник");
        }
        if (doc.getWarehouseToId().equals(doc.getWarehouseId())) {
            throw new ConflictException("Склад-источник и склад-приёмник должны быть разными");
        }
    }

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        for (StockDocLine line : doc.getLines()) {
            keys.add(new BalanceKey(line.getProductId(), doc.getWarehouseId()));
            keys.add(new BalanceKey(line.getProductId(), doc.getWarehouseToId()));
        }
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        long docSum = 0;
        for (StockDocLine line : doc.getLines()) {
            long cost = ctx.balance(line.getProductId(), doc.getWarehouseId()).issue(line.getQty(), "перемещения");
            ctx.writeMovement(doc, line, line.getQty().negate(), cost, doc.getWarehouseId());

            ctx.writeMovement(doc, line, line.getQty(), cost, doc.getWarehouseToId());
            ctx.balance(line.getProductId(), doc.getWarehouseToId()).receive(line.getQty(), cost);

            line.setCostMinor(cost);
            docSum += CostMath.costSumMinor(line.getQty(), cost);
        }
        return docSum;
    }
}
