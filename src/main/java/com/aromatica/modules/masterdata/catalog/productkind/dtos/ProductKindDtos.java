package com.aromatica.modules.masterdata.catalog.productkind.dtos;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProductKindDtos {

    private ProductKindDtos() {}

    public record KindDto(
            String code,
            String name,
            CatalogProduct.Unit unit,
            boolean system,
            boolean archived,
            boolean giftEligible,
            String icon,
            String color
    ) {}

    public record KindRequest(

            @NotBlank(message = "Не указано название формы")
            @Size(max = 64)
            String name,

            /** null — штуки. Задаётся при создании, потом не меняется. */
            CatalogProduct.Unit unit,

            /** Показывать форму в подарках; null — да. */
            Boolean giftEligible,

            /** Имя lucide-иконки формы для карточки кассы; null — значок по коду формы. */
            @Size(max = 64)
            String icon,

            /** HEX цвета-акцента (#rrggbb); null — цвет по коду формы. */
            @Size(max = 16)
            String color
    ) {}
}
