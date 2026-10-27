package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Разборка: себестоимость вещи делится по текущим средним составляющих, без весов — поровну.
 * Налитое не разбирается: масло назад в бочку не льётся.
 */
@Component
public class DisassemblyPosting extends CompositionPosting {

    @Override
    public DocType type() {
        return DocType.DISASSEMBLY;
    }

    @Override
    protected UUID thingWarehouse(StockDoc doc) {
        return doc.getWarehouseId();
    }

    @Override
    protected UUID partsWarehouse(StockDoc doc) {
        return warehouseTo(doc);
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        StockDocLine target = compositeLine(doc, ctx);
        UUID from = thingWarehouse(doc);
        UUID to = partsWarehouse(doc);
        requireLinesMatchComposition(doc, target, ctx);
        for (StockDocLine line : doc.getLines()) {
            CatalogProduct p = ctx.products().get(line.getProductId());
            if (line != target && p != null && p.getUnit() == CatalogProduct.Unit.ML) {
                throw new ConflictException("Разобрать нельзя: налитое («" + p.getName()
                        + "») обратно в тару не заливается");
            }
        }

        long targetAvg = ctx.balance(target.getProductId(), from).issue(target.getQty(), "разборки");
        long totalCost = CostMath.costSumMinor(target.getQty(), targetAvg);
        ctx.writeMovement(doc, target, target.getQty().negate(), targetAvg, from);
        target.setCostMinor(targetAvg);

        List<StockDocLine> parts = doc.getLines().stream().filter(l -> l != target).toList();
        Map<UUID, StockBalance> balances = new HashMap<>();
        long weightSum = 0;
        for (StockDocLine line : parts) {
            StockBalance b = ctx.balance(line.getProductId(), to);
            balances.put(line.getId(), b);
            weightSum += CostMath.costSumMinor(line.getQty(), b.getAvgCostMinor());
        }
        long distributed = 0;
        for (int i = 0; i < parts.size(); i++) {
            StockDocLine line = parts.get(i);
            StockBalance b = balances.get(line.getId());
            long share;
            if (i == parts.size() - 1) {
                share = totalCost - distributed; // хвост округления — последней строке
            } else if (weightSum > 0) {
                share = BigDecimal.valueOf(totalCost)
                        .multiply(BigDecimal.valueOf(CostMath.costSumMinor(line.getQty(), b.getAvgCostMinor())))
                        .divide(BigDecimal.valueOf(weightSum), 0, RoundingMode.HALF_UP).longValueExact();
            } else {
                share = totalCost / parts.size();
            }
            distributed += share;
            long unitCost = PostingContext.perUnit(share, line.getQty());
            ctx.writeMovement(doc, line, line.getQty(), unitCost, to);
            b.receive(line.getQty(), unitCost);
            line.setCostMinor(unitCost);
        }
        return totalCost;
    }
}
