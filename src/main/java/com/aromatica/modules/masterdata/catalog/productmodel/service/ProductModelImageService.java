package com.aromatica.modules.masterdata.catalog.productmodel.service;

import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImage;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImageSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Единственная точка доступа к репозиторию картинок продуктов. */
public interface ProductModelImageService {

    /** Галерея продукта по порядку, только метаданные без байтов. */
    List<ProductModelImageSummary> listOf(UUID modelId);

    /** Картинка вместе с байтами, для отдачи по ссылке. */
    ProductModelImage find(UUID id);

    /** id основных фотографий пачкой: продукт → картинка. Для списков и кассы. */
    Map<UUID, UUID> mainImageIds(Collection<UUID> modelIds);

    /** Первая загруженная фотография продукта сразу становится основной. */
    ProductModelImage add(UUID modelId, String contentType, byte[] bytes);

    /** С остальных фотографий продукта флаг основной снимается. */
    ProductModelImageSummary makeMain(UUID id);

    /** Если удалили основную, основной становится первая из оставшихся. */
    void delete(UUID id);
}
