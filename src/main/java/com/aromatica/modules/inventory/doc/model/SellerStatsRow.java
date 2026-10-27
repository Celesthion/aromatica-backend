package com.aromatica.modules.inventory.doc.model;

/** Итоги продавца за всё время: продано, чеков, рабочих дней. */
public interface SellerStatsRow {
    long getSoldMinor();
    long getSalesCount();
    long getDaysWorked();
}
