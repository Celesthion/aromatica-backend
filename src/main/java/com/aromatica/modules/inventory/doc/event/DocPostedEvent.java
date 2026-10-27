package com.aromatica.modules.inventory.doc.event;

import com.aromatica.modules.inventory.doc.model.StockDoc;

/**
 * Документ проведён. Слушатели (деньги, сертификат) работают в транзакции проведения: их
 * исключение откатывает и его — «товар списан, а бланк не погашен» не бывает.
 */
public record DocPostedEvent(
        StockDoc doc
) {
}
