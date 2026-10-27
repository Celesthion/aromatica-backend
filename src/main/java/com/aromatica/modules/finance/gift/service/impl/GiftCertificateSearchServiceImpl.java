package com.aromatica.modules.finance.gift.service.impl;

import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.model.QGiftCertificate;
import com.aromatica.modules.finance.gift.repository.GiftCertificateRepository;
import com.aromatica.modules.finance.gift.search.GiftCertificateSearchRequest;
import com.aromatica.modules.finance.gift.service.GiftCertificateSearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificateSearchServiceImpl implements GiftCertificateSearchService {

    GiftCertificateRepository repository;

    @Override
    public Page<GiftCertificate> search(
            GiftCertificateSearchRequest request,
            Collection<UUID> buyerIds
    ) {
        QGiftCertificate c = QGiftCertificate.giftCertificate;
        BooleanBuilder p = new BooleanBuilder();
        if (request.getStatus() != null) {
            p.and(c.status.eq(request.getStatus()));
        }
        String q = request.getQ();
        if (q != null && !q.isBlank()) {
            BooleanBuilder byQ = new BooleanBuilder(c.code.containsIgnoreCase(q.trim()));
            if (buyerIds != null && !buyerIds.isEmpty()) {
                byQ.or(c.buyerId.in(buyerIds));
            }
            p.and(byQ);
        }
        return repository.findAll(p, request);
    }
}
