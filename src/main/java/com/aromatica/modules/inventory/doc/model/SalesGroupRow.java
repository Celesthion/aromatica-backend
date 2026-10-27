package com.aromatica.modules.inventory.doc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Продажи и возвраты, свёрнутые по (день, товар, продавец, тип, подарок); см. StockDocRepository.salesGroups. */
public interface SalesGroupRow {
    LocalDate getDay();
    UUID getProductId();
    UUID getSellerId();
    String getDocType();
    boolean getGift();
    long getRevenueMinor();
    long getCostMinor();
    BigDecimal getQty();
    long getLineCount();
}
