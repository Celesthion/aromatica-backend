package com.aromatica.modules.pos.storefront.service;

import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
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
import com.aromatica.modules.pos.reservation.service.ReservationService.Availability;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.reservation.service.impl.ReservationAvailableStock;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.mapper.PosCatalogMapperImpl;
import com.aromatica.modules.pos.storefront.service.impl.PosCatalogQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Витрина кассы: поиск товара, упаковка, подарки, атомайзеры.
 * Данные синтетические (test-aroma-N), не показатели магазина.
 */
@ExtendWith(MockitoExtension.class)
class PosCatalogQueryServiceTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Mock
    private GiftRuleService giftRuleService;
    @Mock
    private ProductService productService;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private ProductKindService kindService;
    @Mock
    private ProductModelImageService modelImageService;
    @Mock
    private ProductModelService modelService;
    @Mock
    private StockBalanceService stockBalanceService;
    @Mock
    private WarehouseLookup warehouseLookup;
    @Mock
    private ReservationService reservationService;

    private PosCatalogQueryService service;

    @BeforeEach
    void setUp() {
        service = new PosCatalogQueryServiceImpl(giftRuleService, productService, productSearchService, kindService,
                modelImageService, modelService, stockBalanceService, warehouseLookup,
                new ReservationAvailableStock(reservationService), new PosCatalogMapperImpl());
        // по умолчанию фото и моделей нет, тесты про модель настраивают их сами
        lenient().when(modelImageService.mainImageIds(any())).thenReturn(Map.of());
        lenient().when(modelService.findAllById(any())).thenReturn(List.of());
        // доступность считает ReservationService: здесь её собирают из остатков и резервов
        // по складам; броней по умолчанию нет, остаток отдаётся как есть
        lenient().when(productService.findAllById(any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return catalog.stream().filter(p -> ids.contains(p.getId())).toList();
        });
        // каталог отдаёт неархивные товары форм, склад — их остатки
        lenient().when(productService.findActiveByKinds(any())).thenAnswer(inv -> {
            Collection<String> kinds = inv.getArgument(0);
            return catalog.stream().filter(p -> !p.isArchived() && kinds.contains(p.getKind())).toList();
        });
        lenient().when(stockBalanceService.qtyMap(any(), any())).thenAnswer(inv -> {
            Map<UUID, BigDecimal> onHand = stocks.getOrDefault(inv.getArgument(0), Map.of());
            Collection<UUID> ids = inv.getArgument(1);
            Map<UUID, BigDecimal> out = new HashMap<>();
            ids.forEach(id -> {
                if (onHand.containsKey(id)) {
                    out.put(id, onHand.get(id));
                }
            });
            return out;
        });
        lenient().when(reservationService.availability(any(), any(), any())).thenAnswer(inv -> {
            Map<UUID, BigDecimal> onHand = stocks.getOrDefault(inv.getArgument(0), Map.of());
            Map<UUID, BigDecimal> held = reserves.getOrDefault(inv.getArgument(0), Map.of());
            Map<UUID, Availability> out = new HashMap<>();
            for (UUID id : inv.<Collection<UUID>>getArgument(1)) {
                out.put(id, new Availability(onHand.getOrDefault(id, BigDecimal.ZERO),
                        held.getOrDefault(id, BigDecimal.ZERO)));
            }
            return out;
        });
    }

    private final Map<UUID, Map<UUID, BigDecimal>> stocks = new HashMap<>();
    /** Товары каталога: из них отвечают findAllById и отбор «товары формы с остатком». */
    private final List<CatalogProduct> catalog = new ArrayList<>();

    private void givenCatalog(CatalogProduct... products) {
        catalog.addAll(List.of(products));
    }
    private final Map<UUID, Map<UUID, BigDecimal>> reserves = new HashMap<>();

    /** Товар каталога тестовой формы; unit зависит от формы, у наливных это мл. */
    private static CatalogProduct product(
            UUID id,
            CatalogProduct.Kind kind
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(id);
        p.setName("test-" + id);
        p.setKind(kind.name());
        p.setUnit(kind == CatalogProduct.Kind.SPLIT || kind == CatalogProduct.Kind.BULK
                ? CatalogProduct.Unit.ML : CatalogProduct.Unit.PCS);
        p.setPriceMinor(100_000L);
        return p;
    }

    /** Остатки склада: доступность отдаёт их сразу на весь список товаров. */
    private void givenStock(
            UUID warehouse,
            Map<UUID, String> qty
    ) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        qty.forEach((id, q) -> map.put(id, new BigDecimal(q)));
        stocks.put(warehouse, map);
    }

    private static GiftItem itemProduct(
            UUID productId,
            String qty
    ) {
        GiftItem it = new GiftItem();
        it.setProductId(productId);
        it.setQty(new BigDecimal(qty));
        return it;
    }

    private static GiftItem itemKind(
            String kind,
            String qty
    ) {
        GiftItem it = new GiftItem();
        it.setKind(kind);
        it.setQty(new BigDecimal(qty));
        return it;
    }

    private static GiftRule rule(GiftItem... items) {
        GiftRule r = new GiftRule();
        r.setId(UUID.randomUUID());
        r.setName("Тестовая акция");
        r.getItems().addAll(List.of(items));
        return r;
    }

    private static ProductKind kind(
            String code,
            String name
    ) {
        ProductKind k = new ProductKind();
        k.setCode(code);
        k.setName(name);
        return k;
    }

    /** Окружение giftRules: активные правила, товары-подарки, товары упомянутых форм и имена форм. */
    private void givenGiftWorld(
            List<GiftRule> rules,
            List<CatalogProduct> specific,
            List<CatalogProduct> ofKinds,
            Map<UUID, String> stock,
            ProductKind... kinds
    ) {
        when(giftRuleService.activeRules()).thenReturn(rules);
        catalog.addAll(specific);
        catalog.addAll(ofKinds);
        lenient().when(kindService.findAllById(any())).thenReturn(List.of(kinds));
        givenStock(WAREHOUSE, stock);
    }

    @Test
    void searchProducts_fillsStockAndModelFields() {
        // остаток, фото и модель (бренд и имя) подтягиваются к товарам пачкой
        UUID modelId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        CatalogProduct p = product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE);
        p.setModelId(modelId);
        p.setLabel("чёрный");
        p.setVolumeMl(new BigDecimal("50"));
        ProductModel model = new ProductModel();
        model.setId(modelId);
        model.setBrand("TestBrand");
        model.setName("TestAroma");
        when(productSearchService.searchForPos("аром", 60)).thenReturn(List.of(p));
        when(modelService.findAllById(List.of(modelId))).thenReturn(List.of(model));
        when(modelImageService.mainImageIds(List.of(modelId))).thenReturn(Map.of(modelId, imageId));
        givenStock(WAREHOUSE, Map.of(id("test-aroma-1"), "3"));

        List<PosProduct> result = service.searchProducts("аром", WAREHOUSE);

        assertThat(result).hasSize(1);
        PosProduct dto = result.getFirst();
        assertThat(dto.id()).isEqualTo(id("test-aroma-1"));
        assertThat(dto.qty()).isEqualByComparingTo("3");
        assertThat(dto.priceMinor()).isEqualTo(100_000L);
        assertThat(dto.unit()).isEqualTo(CatalogProduct.Unit.PCS);
        assertThat(dto.kind()).isEqualTo("BOTTLE");
        assertThat(dto.volumeMl()).isEqualByComparingTo("50");
        assertThat(dto.mainImageId()).isEqualTo(imageId);
        assertThat(dto.modelId()).isEqualTo(modelId);
        assertThat(dto.brand()).isEqualTo("TestBrand");
        assertThat(dto.model()).isEqualTo("TestAroma");
        assertThat(dto.label()).isEqualTo("чёрный");
    }

    @Test
    void searchProducts_hidesZeroAndNegativeStock() {
        // продавать в минус нельзя, поэтому позиции с нулевым или отрицательным остатком
        // на витрину не попадают (фильтр withStock)
        when(productSearchService.searchForPos("test", 60)).thenReturn(List.of(
                product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE),
                product(id("test-aroma-2"), CatalogProduct.Kind.BOTTLE),
                product(id("test-aroma-3"), CatalogProduct.Kind.BOTTLE)));
        givenStock(WAREHOUSE, Map.of(
                id("test-aroma-1"), "2",
                id("test-aroma-2"), "0",
                id("test-aroma-3"), "-1"));

        List<PosProduct> result = service.searchProducts("test", WAREHOUSE);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-aroma-1"));
    }

    @Test
    void searchProducts_modelWithoutBrandDoesNotFail() {
        // у служебных товаров вроде упаковки бренда нет, brand=null не должен ронять маппинг
        UUID modelId = UUID.randomUUID();
        CatalogProduct withModel = product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE);
        withModel.setModelId(modelId);
        ProductModel noBrand = new ProductModel();
        noBrand.setId(modelId);
        noBrand.setName("TestAroma");
        CatalogProduct noModel = product(id("test-goods-1"), CatalogProduct.Kind.GOODS); // modelId=null
        when(productSearchService.searchForPos("test", 60)).thenReturn(List.of(withModel, noModel));
        when(modelService.findAllById(List.of(modelId))).thenReturn(List.of(noBrand));
        givenStock(WAREHOUSE, Map.of(id("test-aroma-1"), "1", id("test-goods-1"), "1"));

        List<PosProduct> result = service.searchProducts("test", WAREHOUSE);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().brand()).isNull();
        assertThat(result.getFirst().model()).isEqualTo("TestAroma");
        // у товара без модели все модельные поля пустые, на витрине флакон-заглушка
        assertThat(result.get(1).modelId()).isNull();
        assertThat(result.get(1).brand()).isNull();
        assertThat(result.get(1).mainImageId()).isNull();
    }

    @Test
    void searchProducts_excludesPackagingKeepsSplitAndBulk() {
        // упаковку при сборе заказа не показываем, её выбирают при оплате,
        // а наливные формы (распив, разлив) ищутся как обычные позиции
        when(productSearchService.searchForPos("test", 60)).thenReturn(List.of(
                product(id("test-split-1"), CatalogProduct.Kind.SPLIT),
                product(id("test-pack-1"), CatalogProduct.Kind.PACKAGING),
                product(id("test-bulk-1"), CatalogProduct.Kind.BULK)));
        givenStock(WAREHOUSE, Map.of(
                id("test-split-1"), "10",
                id("test-pack-1"), "10",
                id("test-bulk-1"), "10"));

        List<PosProduct> result = service.searchProducts("test", WAREHOUSE);

        assertThat(result).extracting(PosProduct::id)
                .containsExactly(id("test-split-1"), id("test-bulk-1"));
    }

    @Test
    void searchProducts_fallsBackToDefaultWarehouse() {
        // без склада смены остатки берём со склада по умолчанию
        UUID defaultWh = UUID.randomUUID();
        when(warehouseLookup.defaultWarehouseId()).thenReturn(defaultWh);
        when(productSearchService.searchForPos("test", 60))
                .thenReturn(List.of(product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE)));
        givenStock(defaultWh, Map.of(id("test-aroma-1"), "4"));

        List<PosProduct> result = service.searchProducts("test", null);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-aroma-1"));
        verify(reservationService).availability(eq(defaultWh), any(), any());
    }

    @Test
    void searchProducts_subtractsActiveReservations() {
        // витрина показывает доступное: остаток минус активные брони склада;
        // всё, что целиком забронировано (доступно 0), скрывается
        when(productSearchService.searchForPos("test", 60)).thenReturn(List.of(
                product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE),
                product(id("test-aroma-2"), CatalogProduct.Kind.BOTTLE)));
        givenStock(WAREHOUSE, Map.of(id("test-aroma-1"), "3", id("test-aroma-2"), "1"));
        reserves.put(WAREHOUSE, Map.of(
                id("test-aroma-1"), new BigDecimal("2"),
                id("test-aroma-2"), new BigDecimal("1")));

        List<PosProduct> result = service.searchProducts("test", WAREHOUSE);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-aroma-1"));
        assertThat(result.getFirst().qty()).isEqualByComparingTo("1");
    }

    @Test
    void cardsByIds_keepUnavailableProductsWithZeroQty() {
        // строки брони по остатку не фильтруются: касса всегда может показать бронь,
        // даже если резервы заняли весь остаток (qty = 0, не минус)
        when(productService.findAllById(any()))
                .thenReturn(List.of(product(id("test-aroma-1"), CatalogProduct.Kind.BOTTLE)));
        givenStock(WAREHOUSE, Map.of(id("test-aroma-1"), "1"));
        reserves.put(WAREHOUSE, Map.of(id("test-aroma-1"), new BigDecimal("1")));

        List<PosProduct> result = service.cardsByIds(List.of(id("test-aroma-1")), WAREHOUSE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().qty()).isEqualByComparingTo("0");
        assertThat(result.getFirst().priceMinor()).isEqualTo(100_000L);
    }

    @Test
    void packaging_returnsActivePackagingInStock() {
        // упаковка — активные товары формы PACKAGING, без остатка не показываются
        givenCatalog(
                product(id("test-box-1"), CatalogProduct.Kind.PACKAGING),
                product(id("test-box-2"), CatalogProduct.Kind.PACKAGING));
        givenStock(WAREHOUSE, Map.of(id("test-box-1"), "7", id("test-box-2"), "0"));

        List<PosProduct> result = service.packaging(WAREHOUSE);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-box-1"));
        verify(productService).findActiveByKinds(List.of("PACKAGING"));
    }

    @Test
    void atomizers_returnsActiveAtomizersInStock() {
        // атомайзеры — активные товары формы ATOMIZER с положительным остатком
        givenCatalog(
                product(id("test-atom-10"), CatalogProduct.Kind.ATOMIZER),
                product(id("test-atom-30"), CatalogProduct.Kind.ATOMIZER));
        givenStock(WAREHOUSE, Map.of(id("test-atom-10"), "5", id("test-atom-30"), "0"));

        List<PosProduct> result = service.atomizers(WAREHOUSE);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-atom-10"));
        verify(productService).findActiveByKinds(List.of("ATOMIZER"));
    }

    @Test
    void giftKindProducts_filtersByKindAndStock() {
        // подбор экземпляра под позицию «любой товар формы»: форма как есть плюс фильтр по остатку
        givenCatalog(
                product(id("test-split-1"), CatalogProduct.Kind.SPLIT),
                product(id("test-split-2"), CatalogProduct.Kind.SPLIT));
        givenStock(WAREHOUSE, Map.of(id("test-split-1"), "0", id("test-split-2"), "12"));

        List<PosProduct> result = service.giftKindProducts("SPLIT", WAREHOUSE);

        assertThat(result).extracting(PosProduct::id).containsExactly(id("test-split-2"));
        verify(productService).findActiveByKinds(List.of("SPLIT"));
    }

    @Test
    void giftRules_hiddenWhenSpecificProductShort() {
        // товара-подарка не хватает (нужно 2, на складе 1), правило скрыто
        GiftRule r = rule(itemProduct(id("test-gift-1"), "2"));
        givenGiftWorld(List.of(r),
                List.of(product(id("test-gift-1"), CatalogProduct.Kind.GOODS)),
                List.of(),
                Map.of(id("test-gift-1"), "1"));

        assertThat(service.giftRules(WAREHOUSE)).isEmpty();
    }

    @Test
    void giftRules_hiddenWhenGiftProductArchived() {
        // архивный товар-подарок в остатках не участвует, набор не собрать, правило скрыто
        GiftRule r = rule(itemProduct(id("test-gift-1"), "1"));
        CatalogProduct archived = product(id("test-gift-1"), CatalogProduct.Kind.GOODS);
        archived.setArchived(true);
        givenGiftWorld(List.of(r), List.of(archived), List.of(),
                Map.of(id("test-gift-1"), "5"));

        assertThat(service.giftRules(WAREHOUSE)).isEmpty();
    }

    @Test
    void giftRules_kindItemCoveredByAnotherProductOfKind() {
        // позиция «любой товар формы BULK»: у первого товара формы остатка нет,
        // но второй её покрывает, поэтому правило видно и поля DTO заполнены
        GiftRule r = rule(itemKind("BULK", "5"));
        GiftRuleCondition sum = new GiftRuleCondition();
        sum.setType(GiftRuleCondition.Type.SUM);
        sum.setGroupNo(0);
        sum.setMinSumMinor(500_000L);
        r.getConditions().add(sum);
        r.setStepMinor(200_000L);
        r.setMaxGifts(3);
        givenGiftWorld(List.of(r), List.of(),
                List.of(product(id("test-bulk-1"), CatalogProduct.Kind.BULK),
                        product(id("test-bulk-2"), CatalogProduct.Kind.BULK)),
                Map.of(id("test-bulk-1"), "0", id("test-bulk-2"), "5"),
                kind("BULK", "Разлив"));

        List<PosGiftRule> result = service.giftRules(WAREHOUSE);

        assertThat(result).hasSize(1);
        PosGiftRule dto = result.getFirst();
        assertThat(dto.name()).isEqualTo("Тестовая акция");
        assertThat(dto.stepMinor()).isEqualTo(200_000L);
        assertThat(dto.maxGifts()).isEqualTo(3);
        assertThat(dto.conditions()).hasSize(1);
        assertThat(dto.conditions().getFirst().type()).isEqualTo("SUM");
        assertThat(dto.conditions().getFirst().minSumMinor()).isEqualTo(500_000L);
        // у позиции «любой формы» product пуст, имя формы берётся из справочника
        assertThat(dto.items()).hasSize(1);
        assertThat(dto.items().getFirst().product()).isNull();
        assertThat(dto.items().getFirst().kind()).isEqualTo("BULK");
        assertThat(dto.items().getFirst().kindName()).isEqualTo("Разлив");
        assertThat(dto.items().getFirst().qty()).isEqualByComparingTo("5");
    }

    @Test
    void giftRules_sharedProductNeedsNotCountedTwice() {
        // один товар нужен и конкретной позиции (2 мл), и позиции «любой BULK» (2 мл),
        // а остатка 3 мл. Целиком набор не выдать, правило скрыто: остаток нельзя
        // засчитывать обеим позициям
        GiftRule r = rule(itemProduct(id("test-bulk-1"), "2"), itemKind("BULK", "2"));
        CatalogProduct shared = product(id("test-bulk-1"), CatalogProduct.Kind.BULK);
        givenGiftWorld(List.of(r), List.of(shared), List.of(shared),
                Map.of(id("test-bulk-1"), "3"),
                kind("BULK", "Разлив"));

        assertThat(service.giftRules(WAREHOUSE)).isEmpty();
    }

    @Test
    void giftRules_sharedProductCoveredWithSecondKindProduct() {
        // тот же набор, но у формы есть второй товар: конкретная позиция берёт 2 мл
        // из test-bulk-1, «любой BULK» уходит в test-bulk-2, правило видно
        GiftRule r = rule(itemProduct(id("test-bulk-1"), "2"), itemKind("BULK", "2"));
        CatalogProduct shared = product(id("test-bulk-1"), CatalogProduct.Kind.BULK);
        CatalogProduct other = product(id("test-bulk-2"), CatalogProduct.Kind.BULK);
        givenGiftWorld(List.of(r), List.of(shared), List.of(shared, other),
                Map.of(id("test-bulk-1"), "3", id("test-bulk-2"), "2"),
                kind("BULK", "Разлив"));

        List<PosGiftRule> result = service.giftRules(WAREHOUSE);

        assertThat(result).hasSize(1);
        // конкретная позиция несёт товар с остатком, «любой» только форму
        assertThat(result.getFirst().items().getFirst().product().id()).isEqualTo(id("test-bulk-1"));
        assertThat(result.getFirst().items().get(1).product()).isNull();
        assertThat(result.getFirst().items().get(1).kind()).isEqualTo("BULK");
    }

    /**
     * Одинаковые позиции не перебираются во всех перестановках: одиннадцать «любой BULK по 1»
     * на десяти товарах по 1 мл без свёртки симметрии — это 10! вариантов до отказа.
     */
    @Test
    void giftRules_identicalNeedsFailFast() {
        GiftItem[] items = new GiftItem[11];
        Arrays.fill(items, itemKind("BULK", "1"));
        GiftRule r = rule(items);
        List<CatalogProduct> bulks = new ArrayList<>();
        Map<UUID, String> stock = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            bulks.add(product(id("test-bulk-" + i), CatalogProduct.Kind.BULK));
            stock.put(id("test-bulk-" + i), "1");
        }
        givenGiftWorld(List.of(r), List.of(), bulks, stock, kind("BULK", "Разлив"));

        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertThat(service.giftRules(WAREHOUSE)).isEmpty());
    }

    @Test
    void giftRules_backtrackingFindsAssignmentGreedyWouldMiss() {
        // три позиции «любой BULK» (4+3+3 мл) на остатках 6 и 4 мл. Жадный подбор
        // «крупные в первый подходящий» кладёт 4 в 6, и двум по 3 места не хватает.
        // Полный перебор находит раскладку 4 в 4, 3+3 в 6, и правило должно быть видно
        GiftRule r = rule(itemKind("BULK", "4"), itemKind("BULK", "3"), itemKind("BULK", "3"));
        givenGiftWorld(List.of(r), List.of(),
                List.of(product(id("test-bulk-1"), CatalogProduct.Kind.BULK),
                        product(id("test-bulk-2"), CatalogProduct.Kind.BULK)),
                Map.of(id("test-bulk-1"), "6", id("test-bulk-2"), "4"),
                kind("BULK", "Разлив"));

        assertThat(service.giftRules(WAREHOUSE)).hasSize(1);
    }

    @Test
    void giftRules_unknownKindNameFallsBackToCode() {
        // формы нет в справочнике: на кассе показываем её код и не падаем
        GiftRule r = rule(itemKind("CUSTOM", "1"));
        // форма заведена вручную, поэтому kind задаём кодом, а не системным enum
        CatalogProduct custom = product(id("test-custom-1"), CatalogProduct.Kind.GOODS);
        custom.setKind("CUSTOM");
        givenGiftWorld(List.of(r), List.of(), List.of(custom),
                Map.of(id("test-custom-1"), "2"));

        List<PosGiftRule> result = service.giftRules(WAREHOUSE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().items().getFirst().kindName()).isEqualTo("CUSTOM");
    }

    @Test
    void searchProducts_kitQtyIsMinOverComponents() {
        // комплект на складе магазина виртуальный: доступно столько, сколько можно
        // собрать из составляющих, то есть минимум по составу
        CatalogProduct kit = product(id("test-kit-1"), CatalogProduct.Kind.KIT);
        when(productSearchService.searchForPos("kit", 60)).thenReturn(List.of(kit));
        ProductComponent box = new ProductComponent();
        box.setComponentId(id("test-box-1"));
        box.setQty(BigDecimal.ONE);
        ProductComponent bottle = new ProductComponent();
        bottle.setComponentId(id("test-bottle-1"));
        bottle.setQty(new BigDecimal("2"));
        when(productService.componentsOf(List.of(id("test-kit-1"))))
                .thenReturn(Map.of(id("test-kit-1"), List.of(box, bottle)));
        givenStock(WAREHOUSE, Map.of(id("test-box-1"), "10", id("test-bottle-1"), "5"));

        List<PosProduct> result = service.searchProducts("kit", WAREHOUSE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().qty()).isEqualByComparingTo("2"); // флаконов 5, на комплект нужно два
    }
}
