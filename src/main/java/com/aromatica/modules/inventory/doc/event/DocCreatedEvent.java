package com.aromatica.modules.inventory.doc.event;

import com.aromatica.modules.inventory.doc.model.StockDoc;

/** Документ создан (черновик). Лента активности пишет строку «Создание». */
public record DocCreatedEvent(
        StockDoc doc
) {
}
