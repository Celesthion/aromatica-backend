package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;

import java.util.Set;
import java.util.UUID;

/** Документ, который проводится строка за строкой на склад строки; итог — сумма вкладов строк. */
public abstract class LinePosting implements DocPosting {

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        lineKeys(doc, keys);
    }

    static void lineKeys(
            StockDoc doc,
            Set<BalanceKey> keys
    ) {
        doc.getLines().forEach(line ->
                keys.add(new BalanceKey(line.getProductId(), PostingContext.lineWarehouse(doc, line))));
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        long sum = 0;
        for (StockDocLine line : doc.getLines()) {
            sum += postLine(doc, line, PostingContext.lineWarehouse(doc, line), ctx);
        }
        return sum;
    }

    /** Возвращает вклад строки в итог документа. */
    protected abstract long postLine(StockDoc doc, StockDocLine line, UUID warehouse, PostingContext ctx);
}
