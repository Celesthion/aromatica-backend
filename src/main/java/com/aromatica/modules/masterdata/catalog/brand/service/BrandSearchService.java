package com.aromatica.modules.masterdata.catalog.brand.service;

import com.aromatica.modules.masterdata.catalog.brand.model.Brand;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSearchRequest;
import org.springframework.data.domain.Page;

/** Поиск брендов для списков: имя подстрокой, архивные по запросу. */
public interface BrandSearchService {

    Page<Brand> search(BrandSearchRequest request);
}
