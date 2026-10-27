package com.aromatica.modules.masterdata.catalog.product.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductDto;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductRequest;
import com.aromatica.modules.masterdata.catalog.product.endpoint.ProductEndpoint;
import com.aromatica.modules.masterdata.catalog.product.mapper.CatalogProductMapper;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.search.ProductSearchRequest;
import com.aromatica.modules.masterdata.catalog.product.service.ProductEditService;
import com.aromatica.modules.masterdata.catalog.product.service.ProductSearchService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductEndpointImpl implements ProductEndpoint {

    ProductEditService editService;
    ProductSearchService searchService;
    CatalogProductMapper mapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(ProductSearchRequest request) {
        Page<CatalogProduct> page = searchService.search(request);
        return pageMapper.toResponse(page, mapper.toDtos(page.getContent()));
    }

    @Override
    @Transactional
    public ProductDto create(ProductRequest request) {
        return mapper.toDto(editService.create(mapper.toEntity(request), mapper.toComponents(request.components())));
    }

    @Override
    @Transactional
    public ProductDto update(
            UUID id,
            ProductRequest request
    ) {
        return mapper.toDto(editService.update(id, mapper.toEntity(request), mapper.toComponents(request.components())));
    }

    @Override
    @Transactional
    public ProductDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(editService.setArchived(id, archived));
    }
}
