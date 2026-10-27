package com.aromatica.modules.masterdata.catalog.productmodel.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.masterdata.catalog.productmodel.model.QProductModel;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum ProductModelSort implements SortBy {

    BRAND {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(
                    new OrderSpecifier<>(directionToOrder(direction), QProductModel.productModel.brand),
                    new OrderSpecifier<>(directionToOrder(direction), QProductModel.productModel.name));
        }
    },
    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QProductModel.productModel.name));
        }
    }
}
