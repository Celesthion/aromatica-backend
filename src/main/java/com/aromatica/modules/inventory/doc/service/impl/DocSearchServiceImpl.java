package com.aromatica.modules.inventory.doc.service.impl;

import com.aromatica.modules.inventory.doc.model.QStockDoc;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import com.aromatica.modules.inventory.doc.search.DocSearchRequest;
import com.aromatica.modules.inventory.doc.service.DocSearchService;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocSearchServiceImpl implements DocSearchService {

    StockDocRepository docRepository;
    CounterpartySearchService counterpartySearchService;

    @Override
    public Page<StockDoc> search(DocSearchRequest request) {
        QStockDoc doc = QStockDoc.stockDoc;
        BooleanBuilder p = new BooleanBuilder();
        if (request.getType() != null) {
            p.and(doc.docType.eq(request.getType()));
        }
        if (request.getStatus() != null) {
            p.and(doc.status.eq(request.getStatus()));
        }
        if (request.getFrom() != null) {
            p.and(doc.moment.goe(request.getFrom()));
        }
        if (request.getTo() != null) {
            p.and(doc.moment.lt(request.getTo()));
        }
        String q = blankToNull(request.getQ());
        if (q != null && q.matches("\\d{1,18}")) {
            // номер — по индексу, без OR с именем: OR заставлял проходить весь журнал
            p.and(doc.number.eq(Long.parseLong(q)));
        } else if (q != null) {
            // имя сводим к id контрагентов (их мало), а документы ищем по индексу контрагента
            List<UUID> counterpartyIds = counterpartySearchService.idsByName(q);
            if (counterpartyIds.isEmpty()) {
                return Page.empty(PageRequest.of(request.getPage(), request.getSize()));
            }
            p.and(doc.counterpartyId.in(counterpartyIds));
        }
        return docRepository.findAll(p, request);
    }
}
