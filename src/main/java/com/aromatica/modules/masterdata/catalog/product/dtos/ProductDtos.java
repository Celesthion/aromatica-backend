package com.aromatica.modules.masterdata.catalog.product.dtos;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {

    private ProductDtos() {}

    public record ProductDto(

            UUID id,

            String name,

            /** Отличие варианта, например цвет. Входит в полное имя. */
            String label,

            String article,

            Long priceKopecks,

            String description,

            boolean archived,

            UUID modelId,

            String modelBrand,

            String modelName,

            String kind,

            CatalogProduct.Unit unit,

            BigDecimal volumeMl,

            /** Основное фото продукта, к которому относится товар; null — фото нет. */
            UUID mainImageId,

            /** Состав комплекта или готового; у остальных форм пусто. */
            List<ComponentDto> components
    ) {}

    /** Строка состава для показа: составляющая с именем и количеством на штуку. */
    public record ComponentDto(
            UUID productId,
            String name,
            BigDecimal qty,
            CatalogProduct.Unit unit,
            String kind
    ) {}

    /** Строка состава при создании/правке комплекта. */
    public record ComponentRequest(

            @NotNull(message = "Не указана составляющая")
            UUID productId,

            @NotNull(message = "Не указано количество составляющей")
            @Positive(message = "Количество составляющей должно быть больше нуля")
            BigDecimal qty
    ) {}

    public record ProductRequest(

            /** Отличие варианта, например цвет; необязательно. Полное имя собирается само. */
            @Size(max = 256)
            String label,

            @PositiveOrZero(message = "Цена не может быть отрицательной")
            Long priceKopecks,

            @Size(max = 4000)
            String description,

            @NotNull(message = "Выберите продукт")
            UUID modelId,

            String kind,

            CatalogProduct.Unit unit,

            @Positive(message = "Объём должен быть больше нуля")
            BigDecimal volumeMl,

            /** Состав принимается только у комплекта (форма KIT). */
            @Valid
            List<ComponentRequest> components
    ) {}
}
