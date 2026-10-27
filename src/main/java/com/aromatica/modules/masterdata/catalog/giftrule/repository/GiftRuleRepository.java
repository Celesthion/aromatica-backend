package com.aromatica.modules.masterdata.catalog.giftrule.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;

import java.util.List;
import java.util.UUID;

public interface GiftRuleRepository extends EntityRepository<GiftRule, UUID> {

    List<GiftRule> findAllByOrderByCreatedAtAsc();

    List<GiftRule> findByActiveTrueOrderByCreatedAtAsc();
}
