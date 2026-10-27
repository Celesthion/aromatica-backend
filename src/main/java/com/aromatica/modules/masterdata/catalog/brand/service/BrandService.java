package com.aromatica.modules.masterdata.catalog.brand.service;

import com.aromatica.modules.masterdata.catalog.brand.model.Brand;

import java.util.UUID;

/**
 * Справочник брендов, имя уникально без учёта регистра. Новое имя в product_model.brand
 * переносит endpoint. Поиск — в {@link BrandSearchService}.
 */
public interface BrandService {

    Brand find(UUID id);

    Brand create(Brand brand);

    Brand update(UUID id, Brand changes);

    Brand setArchived(UUID id, boolean archived);
}
