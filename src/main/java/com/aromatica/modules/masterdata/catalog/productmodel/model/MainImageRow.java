package com.aromatica.modules.masterdata.catalog.productmodel.model;

import java.util.UUID;

/** Основная фотография продукта: спискам и кассе нужен только id для ссылки. */
public interface MainImageRow {
    UUID getModelId();
    UUID getImageId();
}
