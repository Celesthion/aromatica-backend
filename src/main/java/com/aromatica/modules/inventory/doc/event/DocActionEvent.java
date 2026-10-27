package com.aromatica.modules.inventory.doc.event;

import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;

import java.time.LocalDateTime;
import java.util.UUID;

/** Записано событие журнала документа (проведение, оплата, сторно, …) — для ленты активности. */
public record DocActionEvent(
        StockDoc doc,
        DocEvent.Action action,
        UUID actorId,
        LocalDateTime at
) {
}
