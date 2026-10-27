package com.aromatica.modules.masterdata.catalog.giftrule.endpoint.impl;

import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.endpoint.GiftRuleEndpoint;
import com.aromatica.modules.masterdata.catalog.giftrule.mapper.GiftRuleMapper;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftRuleEndpointImpl implements GiftRuleEndpoint {

    GiftRuleService giftRuleService;
    GiftRuleMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<RuleDto> list() {
        return mapper.toDtos(giftRuleService.list());
    }

    @Override
    @Transactional
    public RuleDto create(RuleRequest request) {
        return mapper.toDto(giftRuleService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public RuleDto update(
            UUID id,
            RuleRequest request
    ) {
        return mapper.toDto(giftRuleService.update(id, mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public RuleDto toggleActive(UUID id) {
        return mapper.toDto(giftRuleService.toggleActive(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        giftRuleService.delete(id);
    }
}
