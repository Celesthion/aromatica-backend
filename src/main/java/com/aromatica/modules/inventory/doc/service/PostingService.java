package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.StockDoc;

import java.util.UUID;

/**
 * Проведение и распроведение складских документов; правила типов — в стратегиях DocPosting.
 * Распроведение удаляет движения документа и пересчитывает балансы по журналу.
 */
public interface PostingService {

    StockDoc post(UUID docId);

    StockDoc unpost(UUID docId);
}
