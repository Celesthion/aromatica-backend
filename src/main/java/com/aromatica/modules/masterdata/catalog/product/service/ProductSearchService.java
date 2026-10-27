package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.search.ProductSearchRequest;
import org.springframework.data.domain.Page;

import java.util.List;

/** Поиск товаров: список каталога с фильтрами и подсказки кассы. */
public interface ProductSearchService {

    Page<CatalogProduct> search(ProductSearchRequest request);

    /** Поиск для кассы по имени или артикулу, только неархивные, не больше limit. */
    List<CatalogProduct> searchForPos(String q, int limit);
}
