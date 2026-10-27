package com.aromatica.modules.inventory.doc.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.inventory.doc.model.QStockDoc;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum DocSort implements SortBy {

    DATE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(
                    new OrderSpecifier<>(directionToOrder(direction), QStockDoc.stockDoc.moment),
                    new OrderSpecifier<>(directionToOrder(direction), QStockDoc.stockDoc.number));
        }
    },
    NUMBER {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QStockDoc.stockDoc.number));
        }
    },
    STATUS {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QStockDoc.stockDoc.status));
        }
    },
    SUM {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QStockDoc.stockDoc.sumMinor));
        }
    }
}
