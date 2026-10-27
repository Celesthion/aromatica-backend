package com.aromatica.modules.masterdata.catalog.productmodel.model;

import java.util.UUID;

/** Фото без байтов: Spring Data выбирает только эти колонки, список не тянет мегабайты картинок. */
public interface ProductModelImageSummary {

    UUID getId();

    UUID getModelId();

    boolean isMain();

    String getContentType();

    int getSizeBytes();
}
