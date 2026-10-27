package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Сборка и разборка. Строки обязаны сойтись с составом карточки × количество вещи — иначе
 * на полке лежала бы вещь, внутри которой не то, что написано.
 */
public abstract class CompositionPosting implements DocPosting {

    @Override
    public boolean allowsCompositeOffShelf(
            StockDocLine line,
            PostingContext ctx
    ) {
        return true;
    }

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        for (StockDocLine line : doc.getLines()) {
            boolean thing = ctx.isComposite(line.getProductId());
            keys.add(new BalanceKey(line.getProductId(), thing ? thingWarehouse(doc) : partsWarehouse(doc)));
        }
    }

    /** Склад, где оказывается или откуда уходит вещь. */
    protected abstract UUID thingWarehouse(StockDoc doc);

    protected abstract UUID partsWarehouse(StockDoc doc);

    protected static UUID warehouseTo(StockDoc doc) {
        return doc.getWarehouseToId() != null ? doc.getWarehouseToId() : doc.getWarehouseId();
    }

    protected static StockDocLine compositeLine(
            StockDoc doc,
            PostingContext ctx
    ) {
        List<StockDocLine> composite = doc.getLines().stream()
                .filter(l -> ctx.isComposite(l.getProductId())).toList();
        if (composite.size() != 1) {
            throw new ConflictException("В сборке и разборке должна быть ровно одна вещь с составом");
        }
        return composite.getFirst();
    }

    protected static void requireLinesMatchComposition(
            StockDoc doc,
            StockDocLine target,
            PostingContext ctx
    ) {
        Map<UUID, BigDecimal> expected = new HashMap<>();
        for (ProductComponent c : ctx.componentsOf(target.getProductId())) {
            expected.merge(c.getComponentId(), c.getQty().multiply(target.getQty()), BigDecimal::add);
        }
        if (expected.isEmpty()) {
            throw new ConflictException("У вещи не задан состав — собирать нечего");
        }
        Map<UUID, BigDecimal> actual = new HashMap<>();
        for (StockDocLine line : doc.getLines()) {
            if (line != target) {
                actual.merge(line.getProductId(), line.getQty(), BigDecimal::add);
            }
        }
        if (!actual.keySet().equals(expected.keySet())
                || expected.entrySet().stream()
                        .anyMatch(e -> actual.get(e.getKey()).compareTo(e.getValue()) != 0)) {
            throw new ConflictException("Строки документа не сходятся с составом вещи");
        }
    }
}
