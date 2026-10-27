package com.aromatica.modules.inventory.doc.model;

import java.time.LocalDate;
import java.util.UUID;

/** Число проведённых чеков продажи за день у продавца. */
public interface SalesCountRow {
    LocalDate getDay();
    UUID getSellerId();
    long getSales();
}
