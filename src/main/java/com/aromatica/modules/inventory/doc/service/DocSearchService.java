package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.search.DocSearchRequest;
import org.springframework.data.domain.Page;

/** Поиск документов для журнала. */
public interface DocSearchService {

    Page<StockDoc> search(DocSearchRequest request);
}
