package com.aromatica.modules.masterdata.catalog.productkind.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;

import java.util.List;
import java.util.Optional;

public interface ProductKindRepository extends EntityRepository<ProductKind, String> {

    /** Сначала системные (в порядке начальных данных), затем пользовательские по дате создания. */
    List<ProductKind> findAllByOrderBySystemDescCreatedAtAsc();

    Optional<ProductKind> findByNameIgnoreCase(String name);
}
