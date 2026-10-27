package com.aromatica.common.search;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;

/** Страница и сортировка поиска (тело POST /search); модуль наследует и добавляет фильтры. */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseSearchRequest<S extends SortBy> {

    Integer page = 0;
    Integer size = 20;

    S sortBy;
    Sort.Direction direction;

    protected abstract S getDefaultSortBy();

    /** Пагинация только через Paging: клиентский size не должен уронить сервер. */
    public int getPage() {
        return Paging.page(page);
    }

    public int getSize() {
        return Paging.size(size);
    }

    public S getSortBy() {
        return sortBy != null ? sortBy : getDefaultSortBy();
    }

    protected Sort.Direction getDefaultDirection() {
        return Sort.Direction.ASC;
    }

    /** Явный {@code "direction": null} от клиента — то же, что не указать порядок. */
    public Sort.Direction getDirection() {
        return direction != null ? direction : getDefaultDirection();
    }
}
