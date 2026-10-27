package com.aromatica.modules.pos.storefront.dtos;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Витрина кассы: карточки товаров, полка, правила подарков. Деньги в минорных единицах (тыйын). */
public final class StorefrontDtos {

    private StorefrontDtos() {}

    /** warehouseId полки — склад для строк чека. */
    public record PosShelf(
            UUID warehouseId,
            List<PosProduct> items
    ) {}

    /** qty — остаток на складе смены. Для товаров в мл {@code priceMinor} — цена за 1 мл. */
    public record PosProduct(

            UUID id,

            String name,

            Long priceMinor,

            CatalogProduct.Unit unit,

            String kind,

            BigDecimal qty,

            BigDecimal volumeMl,

            // null — касса рисует флакон-заглушку
            UUID mainImageId,

            // касса группирует карточки по (modelId, kind)
            UUID modelId,

            // brand и model — заголовок сгруппированной карточки
            String brand,

            String model,

            // чем вариант отличается (цвет и т.п.), подпись при выборе
            String label
    ) {

        /** Та же карточка с остатком, увеличенным на {@code extra}. */
        public PosProduct plusQty(BigDecimal extra) {
            return new PosProduct(id, name, priceMinor, unit, kind, qty.add(extra), volumeMl,
                    mainImageId, modelId, brand, model, label);
        }
    }

    /** Срабатывание касса считает сама по составу чека. Внутри group условия через «И», между группами «ИЛИ». */
    public record PosCondition(
            String type,
            int group,
            Long minSumMinor,
            String kind,
            BigDecimal minVolumeMl,
            UUID productId
    ) {}

    /** product == null — подходит любой товар формы kind, конкретный выбирают на кассе. */
    public record PosItem(
            PosProduct product,
            String kind,
            String kindName,
            BigDecimal qty
    ) {}

    /**
     * stepMinor — ещё подарок за каждые N сом сверх порога SUM, maxGifts — потолок на чек.
     * Набор items выдаётся целиком.
     */
    public record PosGiftRule(
            UUID id,
            String name,
            List<PosCondition> conditions,
            Long stepMinor,
            Integer maxGifts,
            List<PosItem> items
    ) {}
}
