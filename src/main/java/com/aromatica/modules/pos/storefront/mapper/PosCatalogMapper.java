package com.aromatica.modules.pos.storefront.mapper;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosCondition;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosItem;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Витрина кассы → DTO. Остатки, модели, фото и имена форм сервис собирает пачкой
 * на весь список и передаёт сюда.
 */
@Mapper
public interface PosCatalogMapper {

    /** Для товаров в мл цена — за 1 мл, она хранится прямо на товаре. */
    default PosProduct toPosProduct(CatalogProduct product, BigDecimal qty, @Nullable ProductModel model, @Nullable UUID mainImageId) {
        return new PosProduct(product.getId(), product.getName(), product.getPriceMinor(),
                product.getUnit(), product.getKind(), qty, product.getVolumeMl(),
                mainImageId, product.getModelId(),
                model != null ? model.getBrand() : null,
                model != null ? model.getName() : null,
                product.getLabel());
    }

    @Mapping(target = "group", source = "groupNo")
    PosCondition toPosCondition(GiftRuleCondition condition);

    /** stock — карточки товаров-подарков с остатком, kindNames — имена форм («любой Распив»). */
    default PosGiftRule toPosGiftRule(GiftRule rule, Map<UUID, PosProduct> stock, Map<String, String> kindNames) {
        return new PosGiftRule(rule.getId(), rule.getName(),
                rule.getConditions().stream().map(this::toPosCondition).toList(),
                rule.getStepMinor(), rule.getMaxGifts(),
                rule.getItems().stream().map(it -> toPosItem(it, stock, kindNames)).toList());
    }

    /** Позиция «любой товар формы» несёт форму, конкретная — карточку товара. */
    default PosItem toPosItem(GiftItem item, Map<UUID, PosProduct> stock, Map<String, String> kindNames) {
        if (item.getKind() != null) {
            return new PosItem(null, item.getKind(),
                    kindNames.getOrDefault(item.getKind(), item.getKind()), item.getQty());
        }
        return new PosItem(stock.get(item.getProductId()), null, null, item.getQty());
    }
}
