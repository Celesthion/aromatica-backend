package com.aromatica.modules.inventory.doc.event;

import com.aromatica.modules.inventory.doc.model.StockDoc;

/** Документ распроведён: движения удалены, статус снова DRAFT. Зеркало {@link DocPostedEvent}. */
public record DocUnpostedEvent(
        StockDoc doc
) {
}
