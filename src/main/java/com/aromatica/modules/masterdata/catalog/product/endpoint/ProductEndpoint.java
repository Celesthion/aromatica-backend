package com.aromatica.modules.masterdata.catalog.product.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductDto;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductRequest;
import com.aromatica.modules.masterdata.catalog.product.search.ProductSearchRequest;

import java.util.UUID;

public interface ProductEndpoint {

    PageResponse<ProductDto> search(ProductSearchRequest request);

    ProductDto create(ProductRequest request);

    ProductDto update(UUID id, ProductRequest request);

    ProductDto setArchived(UUID id, boolean archived);
}
