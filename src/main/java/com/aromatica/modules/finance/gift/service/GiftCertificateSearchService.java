package com.aromatica.modules.finance.gift.service;

import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.search.GiftCertificateSearchRequest;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.UUID;

public interface GiftCertificateSearchService {

    /** Покупатель в другом модуле: id совпавших по имени или телефону передаёт вызывающий. */
    Page<GiftCertificate> search(GiftCertificateSearchRequest request, Collection<UUID> buyerIds);
}
