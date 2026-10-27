package com.aromatica.modules.masterdata.catalog.productmodel.service;

import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Продукты (ароматы): заведение, правка, архив. Поиск — в {@link ProductModelSearchService}. */
public interface ProductModelService {

    ProductModel find(UUID id);

    ProductModel get(UUID id);

    List<ProductModel> findAllById(Collection<UUID> ids);

    /** Новый продукт: пара бренд + название уникальна без учёта регистра. */
    ProductModel create(ProductModel model);

    ProductModel update(UUID id, ProductModel changes);

    ProductModel setArchived(UUID id, boolean archived);

    /** Переносит новое имя бренда в product_model.brand, где оно хранится строкой. */
    void renameBrand(String oldName, String newName);
}
