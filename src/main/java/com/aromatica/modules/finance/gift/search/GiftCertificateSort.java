package com.aromatica.modules.finance.gift.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.finance.gift.model.QGiftCertificate;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum GiftCertificateSort implements SortBy {

    CREATED {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QGiftCertificate.giftCertificate.createdAt));
        }
    },
    CODE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QGiftCertificate.giftCertificate.code));
        }
    },
    BALANCE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction),
                    QGiftCertificate.giftCertificate.balanceMinor));
        }
    }
}
