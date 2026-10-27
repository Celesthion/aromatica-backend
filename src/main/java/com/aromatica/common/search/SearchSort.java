package com.aromatica.common.search;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.Expressions;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public final class SearchSort {

    private SearchSort() {
    }

    /**
     * Добивка по id: при равном ключе Postgres может отдавать строки в разном порядке,
     * и при листании они повторяются или пропадают.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Sort withIdTieBreak(Sort sort) {
        if (!(sort instanceof QSort q)) {
            return sort;
        }
        return q.getOrderSpecifiers().stream()
                .map(OrderSpecifier::getTarget)
                .filter(t -> t instanceof Path<?>)
                .map(t -> ((Path<?>) t).getRoot())
                .findFirst()
                .map(root -> (Sort) q.and(new OrderSpecifier(Order.ASC,
                        Expressions.comparablePath(Comparable.class, root, "id"))))
                .orElse(sort);
    }
}
