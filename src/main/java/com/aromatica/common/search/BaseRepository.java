package com.aromatica.common.search;

import com.aromatica.common.persistence.EntityRepository;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** Репозиторий с QueryDSL-поиском по предикату и пагинацией из BaseSearchRequest. */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends EntityRepository<T, ID>, QuerydslPredicateExecutor<T> {

    default Page<T> findAll(Predicate predicate, BaseSearchRequest<? extends SortBy> request) {
        Sort sort = SearchSort.withIdTieBreak(request.getSortBy().getSorting(request.getDirection()));
        PageRequest page = PageRequest.of(request.getPage(), request.getSize(), sort);
        return findAll(predicate, page);
    }

    /** Первые limit строк без count(*): подсказкам поиска число страниц не нужно. */
    default List<T> findTop(Predicate predicate, Sort sort, int limit) {
        return findBy(predicate, q -> q.sortBy(SearchSort.withIdTieBreak(sort)).limit(limit).all());
    }
}
