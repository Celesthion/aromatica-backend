package com.aromatica.security.auth.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.security.auth.model.QAppUser;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum UserSort implements SortBy {

    LOGIN {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QAppUser.appUser.login));
        }
    },
    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QAppUser.appUser.displayName));
        }
    },
    ROLE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QAppUser.appUser.role));
        }
    },
    CREATED {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QAppUser.appUser.createdAt));
        }
    }
}
