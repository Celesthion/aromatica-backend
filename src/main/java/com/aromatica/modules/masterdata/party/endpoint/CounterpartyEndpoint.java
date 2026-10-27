package com.aromatica.modules.masterdata.party.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyDto;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyRequest;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;

import java.util.UUID;

public interface CounterpartyEndpoint {

    PageResponse<CounterpartyDto> search(CounterpartySearchRequest request);

    CounterpartyDto create(CounterpartyRequest request);

    CounterpartyDto update(UUID id, CounterpartyRequest request);

    CounterpartyDto setArchived(UUID id, boolean archived);
}
