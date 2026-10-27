package com.aromatica.modules.masterdata.catalog.productmodel.service.impl;

import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.model.QProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.repository.ProductModelRepository;
import com.aromatica.modules.masterdata.catalog.productmodel.search.ProductModelSearchRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelSearchService;
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
public class ProductModelSearchServiceImpl implements ProductModelSearchService {

    ProductModelRepository repository;

    @Override
    public Page<ProductModel> search(ProductModelSearchRequest request) {
        QProductModel model = QProductModel.productModel;
        BooleanBuilder p = new BooleanBuilder();
        if (!Boolean.TRUE.equals(request.getIncludeArchived())) {
            p.and(model.archived.isFalse());
        }
        if (request.getQ() != null && !request.getQ().isBlank()) {
            String q = request.getQ().trim();
            p.and(model.brand.containsIgnoreCase(q).or(model.name.containsIgnoreCase(q)));
        }
        return repository.findAll(p, request);
    }
}
