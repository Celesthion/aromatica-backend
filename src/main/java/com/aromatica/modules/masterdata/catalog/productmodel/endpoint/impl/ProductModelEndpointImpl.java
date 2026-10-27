package com.aromatica.modules.masterdata.catalog.productmodel.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ModelRow;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelDto;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.endpoint.ProductModelEndpoint;
import com.aromatica.modules.masterdata.catalog.productmodel.mapper.ProductModelMapper;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.search.ProductModelSearchRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelSearchService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelEndpointImpl implements ProductModelEndpoint {

    ProductModelService modelService;
    ProductModelSearchService searchService;
    ProductService productService;
    ProductModelMapper mapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ModelRow> search(ProductModelSearchRequest request) {
        Page<ProductModel> page = searchService.search(request);
        return pageMapper.toResponse(page, mapper.toRows(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductModelDto one(UUID id) {
        return mapper.toDto(modelService.find(id));
    }

    @Override
    @Transactional
    public ProductModelDto create(ProductModelRequest request) {
        return mapper.toDto(modelService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public ProductModelDto update(
            UUID id,
            ProductModelRequest request
    ) {
        ProductModel before = modelService.find(id);
        String oldBrand = before.getBrand();
        String oldName = before.getName();
        ProductModel model = modelService.update(id, mapper.toEntity(request));
        // имя товара хранится готовым: после переименования продукта пересобираем его товары
        if (!Objects.equals(oldBrand, model.getBrand()) || !oldName.equals(model.getName())) {
            productService.recomposeNamesForModel(id);
        }
        return mapper.toDto(model);
    }

    @Override
    @Transactional
    public ProductModelDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(modelService.setArchived(id, archived));
    }
}
