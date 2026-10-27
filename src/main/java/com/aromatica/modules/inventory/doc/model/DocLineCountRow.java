package com.aromatica.modules.inventory.doc.model;

import java.util.UUID;

/** Число строк документа — списку не нужно грузить сами строки. */
public interface DocLineCountRow {
    UUID getDocId();
    long getLineCount();
}
