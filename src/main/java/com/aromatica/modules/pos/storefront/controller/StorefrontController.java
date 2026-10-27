package com.aromatica.modules.pos.storefront.controller;

import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;
import com.aromatica.modules.pos.storefront.endpoint.StorefrontEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: витрина", description = "Товары, карточки, упаковка, атомайзеры, полка и подарочные акции для экрана кассы.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorefrontController {

    StorefrontEndpoint storefrontEndpoint;

    @Operation(summary = "Поиск товаров")
    @GetMapping("/products")
    public List<PosProduct> products(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UUID warehouseId
    ) {
        return storefrontEndpoint.searchProducts(search, warehouseId);
    }

    @Operation(summary = "Карточки товаров по id", description = "Свежие остатки, чтобы восстановить отложенный чек.")
    @GetMapping("/products/cards")
    public List<PosProduct> productCards(
            @RequestParam List<UUID> ids,
            @RequestParam(required = false) UUID warehouseId
    ) {
        return storefrontEndpoint.productCards(ids, warehouseId);
    }

    @Operation(summary = "Упаковка", description = "Предлагается при оплате; в поиск товаров не попадает.")
    @GetMapping("/packaging")
    public List<PosProduct> packaging(@RequestParam(required = false) UUID warehouseId) {
        return storefrontEndpoint.packaging(warehouseId);
    }

    @Operation(summary = "Подарочные акции", description = "Только активные акции, подарки по которым есть на складе.")
    @GetMapping("/gift-rules")
    public List<PosGiftRule> giftRules(@RequestParam(required = false) UUID warehouseId) {
        return storefrontEndpoint.giftRules(warehouseId);
    }

    @Operation(summary = "Содержимое полки")
    @GetMapping("/shelf")
    public PosShelf shelf(@RequestParam(required = false) UUID warehouseId) {
        return storefrontEndpoint.shelf(warehouseId);
    }

    @Operation(summary = "Атомайзеры", description = "Объёмы атомайзеров задают варианты разлива.")
    @GetMapping("/atomizers")
    public List<PosProduct> atomizers(@RequestParam(required = false) UUID warehouseId) {
        return storefrontEndpoint.atomizers(warehouseId);
    }

    @Operation(summary = "Товары формы для подарка", description = "Для выбора конкретного товара в позиции «любой товар формы».")
    @GetMapping("/gift-products")
    public List<PosProduct> giftKindProducts(
            @RequestParam String kind,
            @RequestParam(required = false) UUID warehouseId
    ) {
        return storefrontEndpoint.giftKindProducts(kind, warehouseId);
    }
}
