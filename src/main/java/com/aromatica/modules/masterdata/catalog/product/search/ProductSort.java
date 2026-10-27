package com.aromatica.modules.masterdata.catalog.product.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.masterdata.catalog.product.model.QCatalogProduct;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum ProductSort implements SortBy {

    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCatalogProduct.catalogProduct.name));
        }
    },
    PRICE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCatalogProduct.catalogProduct.priceMinor));
        }
    },
    ARTICLE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCatalogProduct.catalogProduct.article));
        }
    },
    KIND {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCatalogProduct.catalogProduct.kind));
        }
    },
    VOLUME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QCatalogProduct.catalogProduct.volumeMl));
        }
    }
}
