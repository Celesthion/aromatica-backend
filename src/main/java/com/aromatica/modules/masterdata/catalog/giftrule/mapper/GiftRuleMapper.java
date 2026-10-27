package com.aromatica.modules.masterdata.catalog.giftrule.mapper;

import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ConditionDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ConditionRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ItemDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ItemRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ручной маппер: для MapStruct слишком глубокая вложенность. Имена товаров и форм
 * берёт пачкой на весь список, без N+1.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftRuleMapper {

    ProductService productService;
    ProductKindService kindService;

    /** Правило из запроса: только то, что задаёт пользователь; проверяет и раскладывает сервис. */
    public GiftRule toEntity(RuleRequest request) {
        GiftRule rule = new GiftRule();
        rule.setName(request.name());
        rule.setStepMinor(request.stepMinor());
        rule.setMaxGifts(request.maxGifts());
        request.conditions().forEach(c -> rule.getConditions().add(toCondition(c)));
        request.items().forEach(i -> rule.getItems().add(toItem(i)));
        return rule;
    }

    private static GiftRuleCondition toCondition(ConditionRequest request) {
        GiftRuleCondition c = new GiftRuleCondition();
        c.setType(request.type());
        c.setGroupNo(request.group() != null ? request.group() : 0);
        c.setMinSumMinor(request.minSumMinor());
        c.setKind(request.kind());
        c.setMinVolumeMl(request.minVolumeMl());
        c.setProductId(request.productId());
        return c;
    }

    private static GiftItem toItem(ItemRequest request) {
        GiftItem item = new GiftItem();
        item.setProductId(request.productId());
        item.setKind(request.kind());
        item.setQty(request.qty());
        return item;
    }

    public RuleDto toDto(GiftRule rule) {
        return toDtos(List.of(rule)).getFirst();
    }

    public List<RuleDto> toDtos(List<GiftRule> rules) {
        var ids = rules.stream()
                .flatMap(r -> Stream.concat(
                        r.getItems().stream().map(GiftItem::getProductId).filter(Objects::nonNull),
                        r.getConditions().stream().map(GiftRuleCondition::getProductId)
                                .filter(Objects::nonNull)))
                .distinct()
                .toList();
        Map<UUID, CatalogProduct> products = productService.findAllById(ids).stream()
                .collect(Collectors.toMap(CatalogProduct::getId, p -> p));
        var kindCodes = Stream.concat(
                        rules.stream().flatMap(r -> r.getItems().stream()).map(GiftItem::getKind),
                        products.values().stream().map(CatalogProduct::getKind))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, ProductKind> kinds = kindService.findAllById(kindCodes).stream()
                .collect(Collectors.toMap(ProductKind::getCode, k -> k));
        return rules.stream()
                .map(r -> new RuleDto(r.getId(), r.getName(),
                        conditionDtos(r, products),
                        r.getStepMinor(), r.getMaxGifts(),
                        itemDtos(r, products, kinds),
                        r.isActive()))
                .toList();
    }

    private static List<ItemDto> itemDtos(
            GiftRule rule,
            Map<UUID, CatalogProduct> products,
            Map<String, ProductKind> kinds
    ) {
        List<ItemDto> out = new ArrayList<>();
        for (GiftItem it : rule.getItems()) {
            if (it.getProductId() != null) {
                CatalogProduct p = products.get(it.getProductId());
                String kindCode = p != null ? p.getKind() : null;
                ProductKind kd = kindCode != null ? kinds.get(kindCode) : null;
                out.add(new ItemDto(it.getProductId(),
                        p != null ? p.getName() : it.getProductId().toString(),
                        kindCode, kd != null ? kd.getName() : kindCode,
                        p != null ? p.getUnit() : null,
                        it.getQty()));
            } else {
                ProductKind kd = kinds.get(it.getKind());
                out.add(new ItemDto(null, null, it.getKind(),
                        kd != null ? kd.getName() : it.getKind(),
                        kd != null ? kd.getUnit() : null,
                        it.getQty()));
            }
        }
        return out;
    }

    private static List<ConditionDto> conditionDtos(
            GiftRule rule,
            Map<UUID, CatalogProduct> products
    ) {
        List<ConditionDto> out = new ArrayList<>();
        for (GiftRuleCondition c : rule.getConditions()) {
            CatalogProduct p = c.getProductId() != null ? products.get(c.getProductId()) : null;
            String productName = c.getProductId() != null
                    ? (p != null ? p.getName() : c.getProductId().toString()) : null;
            out.add(new ConditionDto(c.getType(), c.getGroupNo(), c.getMinSumMinor(),
                    c.getKind(), c.getMinVolumeMl(), c.getProductId(), productName));
        }
        return out;
    }
}
