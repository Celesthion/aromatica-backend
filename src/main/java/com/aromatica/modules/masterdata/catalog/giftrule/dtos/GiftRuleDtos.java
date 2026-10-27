package com.aromatica.modules.masterdata.catalog.giftrule.dtos;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

public final class GiftRuleDtos {

    private GiftRuleDtos() {}

    /** type = SUM|KIND|PRODUCT; productName — имя товара или формы для показа; group — номер группы «И». */
    public record ConditionDto(
            GiftRuleCondition.Type type,
            int group,
            Long minSumMinor,
            String kind,
            BigDecimal minVolumeMl,
            UUID productId,
            String productName
    ) {}

    /** productId = null — подойдёт любой товар формы kind, конкретный выбирают на кассе. */
    public record ItemDto(
            UUID productId,
            String productName,
            String kind,
            String kindName,
            CatalogProduct.Unit unit,
            BigDecimal qty
    ) {}

    public record RuleDto(
            UUID id,
            String name,
            List<ConditionDto> conditions,
            Long stepMinor,
            Integer maxGifts,
            List<ItemDto> items,
            boolean active
    ) {}

    /** Условие в запросе создания/правки; group — номер группы «И» (null → 0). */
    public record ConditionRequest(
            GiftRuleCondition.Type type,
            Integer group,
            Long minSumMinor,
            String kind,
            BigDecimal minVolumeMl,
            UUID productId
    ) {}

    public record ItemRequest(
            UUID productId,
            String kind,
            BigDecimal qty
    ) {}

    public record RuleRequest(

            @NotBlank(message = "Опишите условие подарка")
            @Size(max = 255)
            String name,

            List<ConditionRequest> conditions,

            @Positive(message = "Шаг должен быть больше нуля")
            Long stepMinor,

            @Positive(message = "Максимум подарков должен быть больше нуля")
            Integer maxGifts,

            List<ItemRequest> items
    ) {
        public RuleRequest {
            conditions = requireNonNullElse(conditions, List.of());
            items = requireNonNullElse(items, List.of());
        }
    }
}
