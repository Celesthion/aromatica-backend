package com.aromatica.modules.pos.storefront.service.impl;

import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductSearchService;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelImageService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;
import com.aromatica.modules.pos.storefront.mapper.PosCatalogMapper;
import com.aromatica.modules.pos.storefront.service.AvailableStock;
import com.aromatica.modules.pos.storefront.service.PosCatalogQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Витрина кассового терминала: товары, упаковка, подарки, атомайзеры. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosCatalogQueryServiceImpl implements PosCatalogQueryService {

    GiftRuleService giftRuleService;
    ProductService productService;
    ProductSearchService productSearchService;
    ProductKindService kindService;
    ProductModelImageService modelImageService;
    ProductModelService modelService;
    StockBalanceService stockBalanceService;
    WarehouseLookup warehouseLookup;
    /** Остаток минус брони — считает модуль броней. */
    AvailableStock availableStock;
    PosCatalogMapper mapper;

    @Override
    public List<PosProduct> searchProducts(
            String search,
            UUID warehouseId
    ) {
        var products = productSearchService.searchForPos(search, 60).stream()
                // упаковку при сборе заказа не показываем: её выбирают в конце, при оплате
                .filter(p -> !p.hasKind(CatalogProduct.Kind.PACKAGING))
                // вода — сырьё: её расход дописывает сервер, отдельно она не продаётся
                .filter(p -> !p.hasKind(CatalogProduct.Kind.BASE))
                .toList();
        return withStock(products, warehouseId);
    }

    @Override
    public List<PosProduct> packaging(UUID warehouseId) {
        return withStock(inStockOfKinds(List.of(CatalogProduct.Kind.PACKAGING.name()), warehouseId), warehouseId);
    }

    @Override
    public List<PosGiftRule> giftRules(UUID warehouseId) {
        List<GiftRule> rules = giftRuleService.activeRules();

        List<UUID> productIds = rules.stream().flatMap(r -> r.getItems().stream())
                .map(GiftItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        Set<String> kindCodes = rules.stream().flatMap(r -> r.getItems().stream())
                .map(GiftItem::getKind)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        // доступность — одним расчётом на все кандидаты, потом раскладываем
        Map<UUID, CatalogProduct> candidates = new LinkedHashMap<>();
        productService.findAllById(productIds).stream()
                .filter(p -> !p.isArchived()).forEach(p -> candidates.put(p.getId(), p));
        inStockOfKinds(kindCodes, warehouseId).forEach(p -> candidates.putIfAbsent(p.getId(), p));
        List<PosProduct> available = withStock(List.copyOf(candidates.values()), warehouseId);
        Set<UUID> specific = Set.copyOf(productIds);
        Map<UUID, PosProduct> stock = new HashMap<>();
        available.stream().filter(p -> specific.contains(p.id())).forEach(p -> stock.put(p.id(), p));
        Map<UUID, BigDecimal> availableQty = new HashMap<>();
        available.forEach(p -> availableQty.put(p.id(), p.qty()));
        Map<String, List<UUID>> idsByKind = available.stream()
                .filter(p -> kindCodes.contains(p.kind()))
                .collect(Collectors.groupingBy(PosProduct::kind, Collectors.mapping(PosProduct::id, Collectors.toList())));
        // имена форм для показа на кассе («любой Распив»)
        Map<String, String> kindNames = kindService.findAllById(kindCodes).stream()
                .collect(Collectors.toMap(ProductKind::getCode, ProductKind::getName));

        return rules.stream()
                .filter(r -> GiftBundleCoverage.coverable(r, availableQty, idsByKind))
                .map(r -> mapper.toPosGiftRule(r, stock, kindNames))
                .toList();
    }

    @Override
    public List<PosProduct> giftKindProducts(
            String kind,
            UUID warehouseId
    ) {
        return withStock(inStockOfKinds(List.of(kind), warehouseId), warehouseId);
    }

    @Override
    public List<PosProduct> atomizers(UUID warehouseId) {
        return withStock(inStockOfKinds(List.of(CatalogProduct.Kind.ATOMIZER.name()), warehouseId), warehouseId);
    }

    /**
     * Товары форм в наличии; остаток отбирает база. У виртуального комплекта строки остатка нет,
     * поэтому его форму берём целиком, а отсеет cards.
     */
    private List<CatalogProduct> inStockOfKinds(
            Collection<String> kinds,
            UUID warehouseId
    ) {
        if (kinds.isEmpty()) {
            return List.of();
        }
        UUID wh = warehouseId != null ? warehouseId : warehouseLookup.defaultWarehouseId();
        // товары форм берём у каталога, остаток — у склада; таблицы друг друга модули не читают
        List<CatalogProduct> ofKinds = productService.findActiveByKinds(kinds);
        Map<UUID, BigDecimal> onHand = stockBalanceService.qtyMap(wh, ofKinds.stream().map(CatalogProduct::getId).toList());
        List<CatalogProduct> products = new ArrayList<>(ofKinds.stream()
                .filter(p -> onHand.getOrDefault(p.getId(), BigDecimal.ZERO).signum() > 0)
                .toList());
        if (kinds.contains(CatalogProduct.Kind.KIT.name()) && !warehouseLookup.isShelf(wh)) {
            products.addAll(productService.findActiveByKind(CatalogProduct.Kind.KIT.name()));
        }
        products.sort(Comparator.comparing(CatalogProduct::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CatalogProduct::getId));
        return products;
    }

    /** Полка: остатки полки склада смены; брони её не касаются. */
    @Override
    public PosShelf shelf(UUID warehouseId) {
        UUID store = warehouseId != null ? warehouseId : warehouseLookup.defaultWarehouseId();
        UUID shelf = warehouseLookup.shelfOf(store);
        List<CatalogProduct> products = productService.findAllById(stockBalanceService.positiveProductIds(shelf));
        return new PosShelf(shelf,
                cards(products.stream().filter(p -> !p.isArchived()).toList(), shelf, true));
    }

    /** Карточки по id без фильтра «в наличии»: строки брони, восстановление черновика. */
    @Override
    public List<PosProduct> cardsByIds(
            Collection<UUID> productIds,
            UUID warehouseId
    ) {
        return cards(productService.findAllById(productIds), warehouseId, false);
    }

    /** Оставляет только позиции с положительным доступным остатком (в минус не продаём). */
    private List<PosProduct> withStock(
            List<CatalogProduct> products,
            UUID warehouseId
    ) {
        return cards(products, warehouseId, true);
    }

    /**
     * Карточки с «доступно» (остаток − брони, не ниже нуля) вместо сырого остатка.
     * onlyAvailable — отсечь позиции без доступного остатка (витрина).
     */
    private List<PosProduct> cards(
            List<CatalogProduct> products,
            UUID warehouseId,
            boolean onlyAvailable
    ) {
        UUID wh = warehouseId != null ? warehouseId : warehouseLookup.defaultWarehouseId();
        Map<UUID, BigDecimal> qty = availableStock.availableQty(wh,
                products.stream().map(CatalogProduct::getId).toList());
        // комплект на складе виртуален — доступно по составляющим; на полке он лежит штуками
        if (!warehouseLookup.isShelf(wh)) {
            kitQty(products, wh, qty);
        }
        // фото и модель (бренд + имя) берём у продукта товара, пачкой на весь список
        List<UUID> modelIds = products.stream()
                .map(CatalogProduct::getModelId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, UUID> mainImages = modelImageService.mainImageIds(modelIds);
        Map<UUID, ProductModel> models = modelService.findAllById(modelIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, m -> m));
        return products.stream()
                .filter(p -> !onlyAvailable
                        || qty.getOrDefault(p.getId(), BigDecimal.ZERO).signum() > 0)
                .map(p -> mapper.toPosProduct(p, qty.getOrDefault(p.getId(), BigDecimal.ZERO),
                        p.getModelId() != null ? models.get(p.getModelId()) : null,
                        p.getModelId() != null ? mainImages.get(p.getModelId()) : null))
                .toList();
    }

    /** Сколько комплектов собирается из доступных составляющих — минимум по составу. */
    private void kitQty(
            List<CatalogProduct> products,
            UUID warehouseId,
            Map<UUID, BigDecimal> qty
    ) {
        List<UUID> kitIds = products.stream()
                .filter(p -> p.hasKind(CatalogProduct.Kind.KIT)).map(CatalogProduct::getId).toList();
        if (kitIds.isEmpty()) {
            return;
        }
        Map<UUID, List<ProductComponent>> compositions = productService.componentsOf(kitIds);
        List<UUID> componentIds = compositions.values().stream().flatMap(List::stream)
                .map(ProductComponent::getComponentId).distinct().toList();
        Map<UUID, BigDecimal> available = availableStock.availableQty(warehouseId, componentIds);
        for (UUID kitId : kitIds) {
            List<ProductComponent> parts = compositions.getOrDefault(kitId, List.of());
            BigDecimal can = null;
            for (ProductComponent c : parts) {
                BigDecimal have = available.getOrDefault(c.getComponentId(), BigDecimal.ZERO);
                BigDecimal n = have.signum() <= 0 ? BigDecimal.ZERO
                        : have.divide(c.getQty(), 0, RoundingMode.DOWN);
                can = can == null ? n : can.min(n);
            }
            qty.put(kitId, can == null ? BigDecimal.ZERO : can);
        }
    }
}
