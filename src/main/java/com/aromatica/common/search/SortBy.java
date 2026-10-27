package com.aromatica.common.search;

import com.querydsl.core.types.Order;
import org.springframework.data.domain.Sort;

/** Вариант сортировки поиска: enum модуля реализует и отдаёт QSort по своему Q-классу. */
public interface SortBy {

    Sort getSorting(Sort.Direction direction);

    default Order directionToOrder(Sort.Direction direction) {
        return switch (direction) {
            case ASC -> Order.ASC;
            case DESC -> Order.DESC;
        };
    }
}
