package com.aromatica.modules.masterdata.party.service.impl;

import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.model.QCounterparty;
import com.aromatica.modules.masterdata.party.repository.CounterpartyRepository;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CounterpartySearchServiceImpl implements CounterpartySearchService {

    CounterpartyRepository repository;

    @Override
    public Page<Counterparty> search(CounterpartySearchRequest request) {
        return repository.findAll(predicate(request), request);
    }

    @Override
    public List<Counterparty> searchTop(CounterpartySearchRequest request) {
        return repository.findTop(predicate(request),
                request.getSortBy().getSorting(request.getDirection()), request.getSize());
    }

    private static BooleanBuilder predicate(CounterpartySearchRequest request) {
        QCounterparty c = QCounterparty.counterparty;
        BooleanBuilder p = new BooleanBuilder();
        if (!Boolean.TRUE.equals(request.getIncludeArchived())) {
            p.and(c.archived.isFalse());
        }
        if (request.getType() != null) {
            p.and(c.type.eq(request.getType()));
        }
        String q = request.getQ();
        if (q != null && !q.isBlank()) {
            String t = q.trim();
            p.and(c.name.containsIgnoreCase(t).or(c.phone.containsIgnoreCase(t)));
        }
        return p;
    }

    @Override
    public List<UUID> idsByName(String part) {
        return repository.findIdsByNameContaining(escapeLike(part.trim()));
    }

    /** % и _ в запросе — буквальные символы, а не шаблон. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
