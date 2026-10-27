package com.aromatica.modules.pos.shift.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.pos.shift.model.QPosShift;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum ShiftSort implements SortBy {

    DATE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QPosShift.posShift.openedAt));
        }
    },
    NUMBER {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QPosShift.posShift.number));
        }
    },
    STATUS {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QPosShift.posShift.status));
        }
    }
}
