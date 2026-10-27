package com.aromatica.modules.masterdata.party.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CounterpartySearchRequest extends BaseSearchRequest<CounterpartySort> {

    /** Подстрока названия или телефона. */
    String q;
    Counterparty.Type type;
    Boolean includeArchived;

    @Override
    protected CounterpartySort getDefaultSortBy() {
        return CounterpartySort.NAME;
    }
}
