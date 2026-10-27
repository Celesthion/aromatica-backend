package com.aromatica.modules.masterdata.party.service;

import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/** Поиск контрагентов: список админки, подсказки кассы, отбор по имени для чужих списков. */
public interface CounterpartySearchService {

    Page<Counterparty> search(CounterpartySearchRequest request);

    /** Первая страница поиска без подсчёта всех совпадений — для подсказок на кассе. */
    List<Counterparty> searchTop(CounterpartySearchRequest request);

    /** id контрагентов с подстрокой в имени — чтобы чужие списки искали по id, а не по имени. */
    List<UUID> idsByName(String part);
}
