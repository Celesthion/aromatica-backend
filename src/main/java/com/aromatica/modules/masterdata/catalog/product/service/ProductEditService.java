package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;

import java.util.List;
import java.util.UUID;

/**
 * Карточка товара глазами администратора: заведение, правка, архив. Здесь правила формы,
 * цены и состава, уникальность и сборка имени; данные — через {@link ProductService}.
 */
public interface ProductEditService {

    /** Новый товар: артикул выдаёт система, имя собирается из частей; состав — только у комплекта. */
    CatalogProduct create(CatalogProduct product, List<ProductComponent> components);

    /** Правка. У готового (READY) меняется только цена: состав система берёт из налитого. */
    CatalogProduct update(UUID id, CatalogProduct changes, List<ProductComponent> components);

    /**
     * Удалять товар нельзя, только архивировать: на него ссылаются документы и движения.
     * Составляющую комплекта в архив не отправить; из архива — по правилам заведения.
     */
    CatalogProduct setArchived(UUID id, boolean archived);
}
