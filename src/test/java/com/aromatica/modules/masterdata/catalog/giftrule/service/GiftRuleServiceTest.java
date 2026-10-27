package com.aromatica.modules.masterdata.catalog.giftrule.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ConditionRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.ItemRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.mapper.GiftRuleMapper;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.giftrule.repository.GiftRuleRepository;
import com.aromatica.modules.masterdata.catalog.giftrule.service.impl.GiftRuleServiceImpl;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Валидация правил подарков: набор позиций, условия И/ИЛИ, кратность. Данные синтетические. */
@ExtendWith(MockitoExtension.class)
class GiftRuleServiceTest {

    @Mock
    private GiftRuleRepository repository;
    @Mock
    private ProductService productService;
    @Mock
    private ProductKindService kindService;

    private GiftRuleService service;
    private GiftRuleMapper mapper;

    /** Запрос проходит тот же путь, что из endpoint: маппер собирает правило, сервис проверяет. */
    private GiftRule create(RuleRequest request) {
        return service.create(mapper.toEntity(request));
    }

    @BeforeEach
    void setUp() {
        service = new GiftRuleServiceImpl(repository, productService, kindService);
        mapper = new GiftRuleMapper(productService, kindService);
        Mockito.lenient().when(repository.save(any(GiftRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Форма в справочнике: активная, разрешена в подарках. */
    private void givenKind(
            String code,
            CatalogProduct.Unit unit
    ) {
        ProductKind kind = new ProductKind();
        kind.setCode(code);
        kind.setName(code);
        kind.setUnit(unit);
        Mockito.lenient().when(kindService.get(code)).thenReturn(Optional.of(kind));
    }

    /** Активный товар каталога. */
    private void givenProduct(
            UUID id,
            CatalogProduct.Unit unit
    ) {
        CatalogProduct product = new CatalogProduct();
        product.setId(id);
        product.setName(id.toString());
        product.setUnit(unit);
        Mockito.lenient().when(productService.find(id)).thenReturn(product);
        Mockito.lenient().when(productService.exists(id)).thenReturn(true);
    }

    // условия без номера группы попадают в группу 0
    private static ConditionRequest sum(long minor) {
        return sum(0, minor);
    }

    private static ConditionRequest sum(
            Integer group,
            long minor
    ) {
        return new ConditionRequest(GiftRuleCondition.Type.SUM, group, minor, null, null, null);
    }

    private static ConditionRequest kind(
            String code,
            BigDecimal minVol
    ) {
        return kind(0, code, minVol);
    }

    private static ConditionRequest kind(
            Integer group,
            String code,
            BigDecimal minVol
    ) {
        return new ConditionRequest(GiftRuleCondition.Type.KIND, group, null, code, minVol, null);
    }

    /** Позиция набора: любой товар формы. */
    private static ItemRequest anyKind(
            String code,
            String qty
    ) {
        return new ItemRequest(null, code, new BigDecimal(qty));
    }

    private static ItemRequest product(
            UUID id,
            String qty
    ) {
        return new ItemRequest(id, null, new BigDecimal(qty));
    }

    private static RuleRequest request(
            List<ConditionRequest> conditions,
            Long step,
            Integer maxGifts,
            List<ItemRequest> items
    ) {
        return new RuleRequest("Тестовая акция", conditions, step, maxGifts, items);
    }

    @Test
    void create_rejectsRuleWithoutItems() {
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("позицию подарка");
    }

    @Test
    void create_rejectsRuleWithoutConditions() {
        givenKind("BULK", CatalogProduct.Unit.ML);
        assertThatThrownBy(() -> create(
                request(List.of(), null, null, List.of(anyKind("BULK", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("хотя бы одно условие");
    }

    @Test
    void create_rejectsZeroItemQty() {
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(anyKind("BULK", "0")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("больше нуля");
    }

    @Test
    void create_rejectsItemWithBothProductAndKind() {
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null,
                        List.of(new ItemRequest(id("p1"), "BULK", new BigDecimal("2"))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("либо любой товар формы");
    }

    @Test
    void create_rejectsItemWithNeitherProductNorKind() {
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null,
                        List.of(new ItemRequest(null, null, new BigDecimal("2"))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("либо любой товар формы");
    }

    @Test
    void create_rejectsUnknownProductInItem() {
        when(productService.find(id("ghost"))).thenThrow(new NotFoundException("нет"));
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(product(id("ghost"), "1")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Неизвестный товар в подарке");
    }

    @Test
    void create_rejectsUnknownKindInItem() {
        when(kindService.get("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(anyKind("NOPE", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Неизвестная форма в подарке");
    }

    @Test
    void create_rejectsArchivedKindInItem() {
        ProductKind kind = new ProductKind();
        kind.setCode("OLD");
        kind.setName("Старая");
        kind.setArchived(true);
        when(kindService.get("OLD")).thenReturn(Optional.of(kind));
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(anyKind("OLD", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("в архиве");
    }

    @Test
    void create_rejectsGiftIneligibleKindInItem() {
        ProductKind kind = new ProductKind();
        kind.setCode("BULK");
        kind.setName("Разлив");
        kind.setUnit(CatalogProduct.Unit.ML);
        kind.setGiftEligible(false);
        when(kindService.get("BULK")).thenReturn(Optional.of(kind));
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(anyKind("BULK", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("выключена для подарков");
    }

    @Test
    void create_rejectsFractionalPcsQty() {
        givenProduct(id("vial"), CatalogProduct.Unit.PCS);
        assertThatThrownBy(() -> create(
                request(List.of(sum(100000)), null, null, List.of(product(id("vial"), "0.5")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("целое число");
    }

    @Test
    void create_rejectsStepWithoutSumCondition() {
        givenKind("BOTTLE", CatalogProduct.Unit.PCS);
        givenKind("BULK", CatalogProduct.Unit.ML);
        assertThatThrownBy(() -> create(
                request(List.of(kind("BOTTLE", null)), 80000L, null, List.of(anyKind("BULK", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("условием по сумме чека");
    }

    @Test
    void create_rejectsConditionWithoutType() {
        // неизвестное значение отсекает Jackson ещё на входе (см. ApiContractTest), здесь — пустое
        givenKind("BULK", CatalogProduct.Unit.ML);
        assertThatThrownBy(() -> create(
                request(List.of(new ConditionRequest(null, 0, null, null, null, null)),
                        null, null, List.of(anyKind("BULK", "2")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Не указан тип условия");
    }

    @Test
    void create_savesBundleWithKindAndProduct() {
        // набор: любой товар формы BULK ×2 и конкретная пробирка ×1; две группы условий работают как «ИЛИ»
        givenKind("BULK", CatalogProduct.Unit.ML);
        givenProduct(id("vial"), CatalogProduct.Unit.PCS);
        GiftRule rule = create(request(
                List.of(sum(0, 100000), kind(1, "BULK", new BigDecimal("10"))),
                null, null,
                List.of(anyKind("BULK", "2"), product(id("vial"), "1"))));
        assertThat(rule.getConditions()).extracting(c -> c.getGroupNo()).containsExactly(0, 1);
        assertThat(rule.getConditions()).hasSize(2);
        assertThat(rule.getItems()).hasSize(2);
        assertThat(rule.getItems().getFirst().getProductId()).isNull();
        assertThat(rule.getItems().getFirst().getKind()).isEqualTo("BULK");
        assertThat(rule.getItems().getFirst().getQty()).isEqualByComparingTo("2");
        assertThat(rule.getItems().get(1).getProductId()).isEqualTo(id("vial"));
        assertThat(rule.getItems().get(1).getKind()).isNull();
        assertThat(rule.getItems().get(1).getQty()).isEqualByComparingTo("1");
    }

    @Test
    void create_putsUngroupedConditionsIntoSingleGroup() {
        givenKind("BULK", CatalogProduct.Unit.ML);
        givenKind("BOTTLE", CatalogProduct.Unit.PCS);
        // без номеров групп всё попадает в одну группу, то есть условия связаны через «И»
        GiftRule rule = create(request(
                List.of(new ConditionRequest(GiftRuleCondition.Type.SUM, null, 100000L, null, null, null),
                        kind(null, "BOTTLE", null)),
                null, null, List.of(anyKind("BULK", "2"))));
        assertThat(rule.getConditions()).extracting(c -> c.getGroupNo()).containsExactly(0, 0);
    }

    @Test
    void create_renumbersGroupsDenselyAndSorted() {
        givenKind("BULK", CatalogProduct.Unit.ML);
        givenKind("BOTTLE", CatalogProduct.Unit.PCS);
        // фронт прислал группы 5 и 2 (группу из середины удалили): приводим их к 0 и 1
        // и складываем условия по группам подряд, касса ждёт готовые «И»-блоки
        GiftRule rule = create(request(
                List.of(sum(5, 100000), kind(2, "BOTTLE", null), sum(5, 300000)),
                null, null, List.of(anyKind("BULK", "2"))));
        assertThat(rule.getConditions()).extracting(c -> c.getGroupNo()).containsExactly(0, 1, 1);
        assertThat(rule.getConditions().getFirst().getKind()).isEqualTo("BOTTLE");
    }
}
