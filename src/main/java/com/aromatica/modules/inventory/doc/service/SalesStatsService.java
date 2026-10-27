package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.SalesCountRow;
import com.aromatica.modules.inventory.doc.model.SalesGroupRow;
import com.aromatica.modules.inventory.doc.model.SellerStatsRow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Агрегаты проведённых продаж для отчётов — считаются в базе, документы не грузятся. */
public interface SalesStatsService {

    SellerStatsRow sellerStats(UUID userId);

    /** Продажи и возвраты периода [from, to), свёрнутые по дню, товару, продавцу, типу и подарку. */
    List<SalesGroupRow> salesGroups(LocalDateTime from, LocalDateTime to);

    /** Число чеков продажи периода [from, to) по дню и продавцу. */
    List<SalesCountRow> salesCounts(LocalDateTime from, LocalDateTime to);
}
