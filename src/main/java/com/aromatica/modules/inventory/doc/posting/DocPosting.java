package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;

import java.util.Set;

/** Правила проведения одного типа документа; общий порядок ведёт PostingService, стратегию берёт из {@link DocPostings}. */
public interface DocPosting {

    DocType type();

    /** Вызывается до блокировок и любой записи. */
    default void validate(StockDoc doc, PostingContext ctx) {
    }

    /** Сервис заблокирует их заранее в общем порядке. */
    void collectBalanceKeys(StockDoc doc, PostingContext ctx, Set<BalanceKey> keys);

    /** Пишет движения в контекст и себестоимость в строки; возвращает итог документа. */
    long post(StockDoc doc, PostingContext ctx);

    /** Сверка оплаты, когда итог уже посчитан. */
    default void settle(StockDoc doc) {
    }

    /** Вещь с составом живёт на полке; на складе магазина — только где разрешит стратегия. */
    default boolean allowsCompositeOffShelf(StockDocLine line, PostingContext ctx) {
        return false;
    }

    /** Себестоимость строк — входные данные, а не итог проведения: распроведение её не снимает. */
    default boolean keepsLineCostsOnUnpost() {
        return false;
    }
}
