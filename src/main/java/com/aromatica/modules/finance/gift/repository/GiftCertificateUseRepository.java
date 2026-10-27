package com.aromatica.modules.finance.gift.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.finance.gift.model.GiftCertificateUse;

import java.util.List;
import java.util.UUID;

public interface GiftCertificateUseRepository extends EntityRepository<GiftCertificateUse, Long> {

    List<GiftCertificateUse> findByCertificateIdOrderByAtDesc(UUID certificateId);

    List<GiftCertificateUse> findByDocIdIn(List<UUID> docIds);
}
