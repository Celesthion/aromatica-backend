package com.aromatica.modules.masterdata.catalog.brand.service.impl;

import com.aromatica.modules.masterdata.catalog.brand.model.Brand;
import com.aromatica.modules.masterdata.catalog.brand.model.QBrand;
import com.aromatica.modules.masterdata.catalog.brand.repository.BrandRepository;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSearchRequest;
import com.aromatica.modules.masterdata.catalog.brand.service.BrandSearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrandSearchServiceImpl implements BrandSearchService {

    BrandRepository repository;

    @Override
    public Page<Brand> search(BrandSearchRequest request) {
        QBrand brand = QBrand.brand;
        BooleanBuilder p = new BooleanBuilder();
        if (!Boolean.TRUE.equals(request.getIncludeArchived())) {
            p.and(brand.archived.isFalse());
        }
        if (request.getQ() != null && !request.getQ().isBlank()) {
            p.and(brand.name.containsIgnoreCase(request.getQ().trim()));
        }
        return repository.findAll(p, request);
    }
}
