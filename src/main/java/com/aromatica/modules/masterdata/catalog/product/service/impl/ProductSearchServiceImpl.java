package com.aromatica.modules.masterdata.catalog.product.service.impl;

import com.aromatica.common.search.SearchTokens;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.QCatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.repository.CatalogProductRepository;
import com.aromatica.modules.masterdata.catalog.product.search.ProductSearchRequest;
import com.aromatica.modules.masterdata.catalog.product.service.ProductSearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.querydsl.QSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductSearchServiceImpl implements ProductSearchService {

    CatalogProductRepository repository;

    @Override
    public Page<CatalogProduct> search(ProductSearchRequest request) {
        return repository.findAll(predicate(request), request);
    }

    @Override
    public List<CatalogProduct> searchForPos(
            String q,
            int limit
    ) {
        QCatalogProduct product = QCatalogProduct.catalogProduct;
        BooleanBuilder p = new BooleanBuilder(product.archived.isFalse());
        nameOrArticle(product, q).ifPresent(p::and);
        return repository.findTop(p, new QSort(product.name.asc()), limit);
    }

    private static BooleanBuilder predicate(ProductSearchRequest request) {
        QCatalogProduct product = QCatalogProduct.catalogProduct;
        BooleanBuilder p = new BooleanBuilder();
        if (!Boolean.TRUE.equals(request.getIncludeArchived())) {
            p.and(product.archived.isFalse());
        }
        String kind = request.getKind();
        if (kind != null && !kind.isBlank()) {
            p.and(product.kind.eq(kind));
        }
        nameOrArticle(product, request.getQ()).ifPresent(p::and);
        if (request.getPriceMinKopecks() != null) {
            p.and(product.priceMinor.goe(request.getPriceMinKopecks()));
        }
        if (request.getPriceMaxKopecks() != null) {
            p.and(product.priceMinor.loe(request.getPriceMaxKopecks()));
        }
        if (request.getVolumeMinMl() != null) {
            p.and(product.volumeMl.goe(request.getVolumeMinMl()));
        }
        if (request.getVolumeMaxMl() != null) {
            p.and(product.volumeMl.loe(request.getVolumeMaxMl()));
        }
        return p;
    }

    /** Каждое слово запроса обязательно: в имени между брендом и моделью « — », «creed aventus» целиком не найдётся. */
    private static Optional<BooleanBuilder> nameOrArticle(
            QCatalogProduct product,
            String query
    ) {
        List<String> tokens = SearchTokens.of(query);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        BooleanBuilder all = new BooleanBuilder();
        for (String token : tokens) {
            all.and(product.name.containsIgnoreCase(token)
                    .or(product.article.containsIgnoreCase(token)));
        }
        return Optional.of(all);
    }
}
