package com.aromatica.modules.inventory.report.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.inventory.stock.model.QStockBalance;
import com.aromatica.modules.masterdata.catalog.product.model.QCatalogProduct;
import com.querydsl.core.types.OrderSpecifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

/** Сортировка остатков: поля позиции из каталога и самого остатка. */
public enum StockSort implements SortBy {

    NAME {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QCatalogProduct.catalogProduct.name));
        }
    },
    QTY {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QStockBalance.stockBalance.qty));
        }
    },
    COST {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QStockBalance.stockBalance.avgCostMinor));
        }
    },
    VALUE {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            QStockBalance b = QStockBalance.stockBalance;
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), b.qty.multiply(b.avgCostMinor)));
        }
    }
}
