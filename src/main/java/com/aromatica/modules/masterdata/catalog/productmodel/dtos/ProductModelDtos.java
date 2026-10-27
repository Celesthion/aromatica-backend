package com.aromatica.modules.masterdata.catalog.productmodel.dtos;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ProductModelDtos {

    private ProductModelDtos() {}

    public record SkuDto(
            UUID id,
            String name,
            String kind,
            CatalogProduct.Unit unit,
            BigDecimal volumeMl,
            boolean archived
    ) {}

    public record ProductModelDto(

            UUID id,

            String brand,

            String name,

            String description,

            ProductModel.Gender gender,

            boolean archived,

            List<SkuDto> skus,

            /** Галерея продукта. Первой идёт основная фотография, её показывают карточки форм. */
            List<ImageDto> images
    ) {}

    /** Фотография без байтов, для галереи и списков. */
    public record ImageDto(
            UUID id,
            boolean main,
            String contentType,
            int sizeBytes
    ) {}

    public record ProductModelRequest(

            /** Бренд необязателен: у служебных продуктов вроде упаковки его нет. */
            @Size(max = 255)
            String brand,

            @NotBlank(message = "У продукта должно быть название")
            @Size(max = 512)
            String name,

            @Size(max = 4000)
            String description,

            ProductModel.Gender gender
    ) {}

    /** Строка списка продуктов: без skus, только число форм. */
    public record ModelRow(

            UUID id,

            String brand,

            String name,

            String description,

            ProductModel.Gender gender,

            boolean archived,

            long forms,

            /** Основная фотография продукта, по её id строится ссылка; null — фото нет. */
            UUID mainImageId
    ) {}
}
