package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Вскрытие: строка 1 — источник (шт), строка 2 — приёмник (мл); себестоимость источника
 * целиком переносится в приёмник.
 */
@Component
public class ConvertPosting implements DocPosting {

    @Override
    public DocType type() {
        return DocType.CONVERT;
    }

    @Override
    public void validate(
            StockDoc doc,
            PostingContext ctx
    ) {
        if (doc.getLines().size() != 2) {
            throw new ConflictException("Вскрытие должно иметь ровно две строки: источник и приёмник");
        }
        if (doc.getLines().getFirst().getProductId().equals(doc.getLines().getLast().getProductId())) {
            throw new ConflictException("Источник и приёмник вскрытия должны быть разными позициями");
        }
        if (doc.getLines().getLast().getQty().signum() <= 0) {
            throw new ConflictException("Объём приёмника должен быть больше нуля");
        }
    }

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        doc.getLines().forEach(line -> keys.add(new BalanceKey(line.getProductId(), doc.getWarehouseId())));
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        StockDocLine source = doc.getLines().getFirst();
        StockDocLine target = doc.getLines().getLast();

        long sourceAvg = ctx.balance(source.getProductId(), doc.getWarehouseId()).issue(source.getQty(), "вскрытия");
        long totalCost = CostMath.costSumMinor(source.getQty(), sourceAvg);
        ctx.writeMovement(doc, source, source.getQty().negate(), sourceAvg, doc.getWarehouseId());
        source.setCostMinor(sourceAvg);

        long targetUnitCost = PostingContext.perUnit(totalCost, target.getQty());
        ctx.writeMovement(doc, target, target.getQty(), targetUnitCost, doc.getWarehouseId());
        ctx.balance(target.getProductId(), doc.getWarehouseId()).receive(target.getQty(), targetUnitCost);
        target.setCostMinor(targetUnitCost);

        return totalCost;
    }
}
