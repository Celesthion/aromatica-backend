package com.aromatica.modules.masterdata.party.service;

import com.aromatica.modules.masterdata.party.model.Counterparty;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Контрагенты: поставщики и покупатели. Поиск — в {@link CounterpartySearchService}. */
public interface CounterpartyService {

    Counterparty find(UUID id);

    /** Имя контрагента или null, для DTO документов. */
    @Nullable String nameOf(@Nullable UUID id);

    Map<UUID, String> names(Collection<UUID> ids);

    Map<UUID, Counterparty> byIds(Collection<UUID> ids);

    /** Тип не задан — поставщик. */
    Counterparty create(Counterparty counterparty);

    /** Тип меняется, только если задан. */
    Counterparty update(UUID id, Counterparty changes);

    Counterparty setArchived(UUID id, boolean archived);
}
