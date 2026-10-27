package com.aromatica.modules.inventory.doc.model;

import java.util.UUID;

/** Долг поставщику: неоплаченные приёмки минус возвраты; контрагент null — документы без поставщика. */
public interface SupplierDebtRow {
    UUID getCounterpartyId();
    long getDebtMinor();
}
