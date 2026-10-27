package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Возврат от покупателя: +qty по себестоимости из строки чека — на полке, куда кладётся
 * возврат, средняя может быть нулевой. Итог — деньги по продажной цене строки.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReturnPosting implements DocPosting {

    StockDocRepository docRepository;

    @Override
    public DocType type() {
        return DocType.RETURN;
    }

    @Override
    public void collectBalanceKeys(
            StockDoc doc,
            PostingContext ctx,
            Set<BalanceKey> keys
    ) {
        LinePosting.lineKeys(doc, keys);
    }

    @Override
    public long post(
            StockDoc doc,
            PostingContext ctx
    ) {
        Map<UUID, Long> sourceCosts = sourceCosts(doc);
        long sum = 0;
        for (StockDocLine line : doc.getLines()) {
            UUID warehouse = PostingContext.lineWarehouse(doc, line);
            StockBalance balance = ctx.balance(line.getProductId(), warehouse);
            // у готового из связки строки в чеке нет — себестоимость заранее в строке возврата; без чека — средняя
            long unitCost = line.getCostMinor() != null ? line.getCostMinor()
                    : sourceCosts.getOrDefault(line.getProductId(), balance.getAvgCostMinor());
            ctx.writeMovement(doc, line, line.getQty(), unitCost, warehouse);
            balance.receive(line.getQty(), unitCost);
            line.setCostMinor(unitCost);
            sum += line.sumMinor();
        }
        return sum;
    }

    @Override
    public void settle(StockDoc doc) {
        ReceiptPayment.settle(doc);
    }

    /** Повторное проведение должно вернуть товар по той же цене, а не по средней полки. */
    @Override
    public boolean keepsLineCostsOnUnpost() {
        return true;
    }

    private Map<UUID, Long> sourceCosts(StockDoc doc) {
        Map<UUID, Long> costs = new HashMap<>();
        if (doc.getSourceDocId() != null) {
            docRepository.findById(doc.getSourceDocId()).ifPresent(source ->
                    source.getLines().forEach(l -> {
                        if (l.getCostMinor() != null) {
                            costs.putIfAbsent(l.getProductId(), l.getCostMinor());
                        }
                    }));
        }
        return costs;
    }
}
