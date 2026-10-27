package com.aromatica.modules.pos.storefront.service;

import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Витрина терминала: карточки — проекция товара, продукта, фото и остатка за вычетом броней.
 */
public interface PosCatalogQueryService {

    /** Только товары с положительным остатком на складе смены: в минус продавать нельзя. */
    List<PosProduct> searchProducts(String search, UUID warehouseId);

    /** Упаковку выбирают в модалке при оплате, поэтому она отдельным списком. */
    List<PosProduct> packaging(UUID warehouseId);

    /**
     * Активные правила, чей набор целиком можно выдать прямо сейчас.
     */
    List<PosGiftRule> giftRules(UUID warehouseId);

    /** Для выбора конкретного товара в позиции «любой товар формы». */
    List<PosProduct> giftKindProducts(String kind, UUID warehouseId);

    /** Объёмы атомайзеров задают варианты разлива на кассе. */
    List<PosProduct> atomizers(UUID warehouseId);

    /** Всё, что лежит на полке склада смены с остатком; продаётся отсюда. */
    PosShelf shelf(UUID warehouseId);

    /**
     * Карточки без фильтра по наличию, qty = max(0, остаток − резервы);
     * резерв самой брони прибавляет её маппер.
     */
    List<PosProduct> cardsByIds(Collection<UUID> productIds, UUID warehouseId);
}
