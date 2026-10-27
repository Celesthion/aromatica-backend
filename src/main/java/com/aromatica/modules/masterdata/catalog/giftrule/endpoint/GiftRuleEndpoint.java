package com.aromatica.modules.masterdata.catalog.giftrule.endpoint;

import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleRequest;

import java.util.List;
import java.util.UUID;

public interface GiftRuleEndpoint {

    List<RuleDto> list();

    RuleDto create(RuleRequest request);

    RuleDto update(UUID id, RuleRequest request);

    RuleDto toggleActive(UUID id);

    void delete(UUID id);
}
