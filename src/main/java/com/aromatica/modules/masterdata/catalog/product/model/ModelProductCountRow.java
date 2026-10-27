package com.aromatica.modules.masterdata.catalog.product.model;

import java.util.UUID;

/** Число форм (SKU) у продукта. */
public interface ModelProductCountRow {
    UUID getModelId();
    long getProducts();
}
