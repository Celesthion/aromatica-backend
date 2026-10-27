package com.aromatica.modules.masterdata.party.mapper;

import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyDto;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyRequest;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper
public interface CounterpartyMapper {

    CounterpartyDto toDto(Counterparty counterparty);

    /** Только то, что задаёт пользователь; id, архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    Counterparty toEntity(CounterpartyRequest request);
}
