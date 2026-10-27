package com.aromatica.modules.masterdata.catalog.productmodel.service;

import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.search.ProductModelSearchRequest;
import org.springframework.data.domain.Page;

/** Поиск продуктов для списков: бренд или название подстрокой, архивные по запросу. */
public interface ProductModelSearchService {

    Page<ProductModel> search(ProductModelSearchRequest request);
}
