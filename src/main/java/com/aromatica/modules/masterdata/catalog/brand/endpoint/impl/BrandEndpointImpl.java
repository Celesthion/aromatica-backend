package com.aromatica.modules.masterdata.catalog.brand.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandDto;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandRequest;
import com.aromatica.modules.masterdata.catalog.brand.endpoint.BrandEndpoint;
import com.aromatica.modules.masterdata.catalog.brand.mapper.BrandMapper;
import com.aromatica.modules.masterdata.catalog.brand.model.Brand;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSearchRequest;
import com.aromatica.modules.masterdata.catalog.brand.service.BrandSearchService;
import com.aromatica.modules.masterdata.catalog.brand.service.BrandService;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
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
public class BrandEndpointImpl implements BrandEndpoint {

    BrandService brandService;
    BrandSearchService searchService;
    ProductModelService modelService;
    ProductService productService;
    BrandMapper mapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BrandDto> search(BrandSearchRequest request) {
        Page<Brand> page = searchService.search(request);
        return pageMapper.toResponse(page, page.getContent().stream().map(mapper::toDto).toList());
    }

    @Override
    @Transactional
    public BrandDto create(BrandRequest request) {
        return mapper.toDto(brandService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public BrandDto update(
            UUID id,
            BrandRequest request
    ) {
        String oldName = brandService.find(id).getName();
        Brand brand = brandService.update(id, mapper.toEntity(request));
        // продукты хранят имя бренда строкой, товары — готовое полное имя
        if (!oldName.equals(brand.getName())) {
            modelService.renameBrand(oldName, brand.getName());
            productService.recomposeNamesForBrand(brand.getName());
        }
        return mapper.toDto(brand);
    }

    @Override
    @Transactional
    public BrandDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(brandService.setArchived(id, archived));
    }
}
