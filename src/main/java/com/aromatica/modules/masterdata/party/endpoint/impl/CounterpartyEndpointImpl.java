package com.aromatica.modules.masterdata.party.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyDto;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyRequest;
import com.aromatica.modules.masterdata.party.endpoint.CounterpartyEndpoint;
import com.aromatica.modules.masterdata.party.mapper.CounterpartyMapper;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CounterpartyEndpointImpl implements CounterpartyEndpoint {

    CounterpartyService counterpartyService;
    CounterpartySearchService searchService;
    CounterpartyMapper mapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CounterpartyDto> search(CounterpartySearchRequest request) {
        Page<Counterparty> page = searchService.search(request);
        return pageMapper.toResponse(page, page.getContent().stream().map(mapper::toDto).toList());
    }

    @Override
    @Transactional
    public CounterpartyDto create(CounterpartyRequest request) {
        return mapper.toDto(counterpartyService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public CounterpartyDto update(
            UUID id,
            CounterpartyRequest request
    ) {
        return mapper.toDto(counterpartyService.update(id, mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public CounterpartyDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(counterpartyService.setArchived(id, archived));
    }
}
