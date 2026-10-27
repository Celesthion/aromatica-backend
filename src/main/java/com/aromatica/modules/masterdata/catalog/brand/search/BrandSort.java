package com.aromatica.modules.masterdata.catalog.brand.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.masterdata.catalog.brand.model.QBrand;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum BrandSort implements SortBy {

    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QBrand.brand.name));
        }
    }
}
