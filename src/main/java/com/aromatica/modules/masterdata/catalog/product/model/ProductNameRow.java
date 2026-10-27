package com.aromatica.modules.masterdata.catalog.product.model;

import java.util.UUID;

/** Имя товара без загрузки сущности. */
public interface ProductNameRow {
    UUID getId();
    String getName();
}
