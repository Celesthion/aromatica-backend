package com.aromatica.modules.masterdata.party.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.masterdata.party.model.QCounterparty;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum CounterpartySort implements SortBy {

    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCounterparty.counterparty.name));
        }
    },
    TYPE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCounterparty.counterparty.type));
        }
    },
    PHONE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCounterparty.counterparty.phone));
        }
    }
}
