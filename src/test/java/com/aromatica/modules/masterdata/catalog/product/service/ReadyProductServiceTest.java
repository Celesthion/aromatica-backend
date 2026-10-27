package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ReadyProductService.Part;
import com.aromatica.modules.masterdata.catalog.product.service.impl.ReadyProductServiceImpl;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Готовое — налитый флакон, который кладут на полку: товар заводится по составу один раз. */
@ExtendWith(MockitoExtension.class)
class ReadyProductServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductModelService modelService;
    @Mock
    private ProductKindService kindService;

    private ReadyProductService service;

    private final ProductModel aroma = new ProductModel();
    private final CatalogProduct oil = product(CatalogProduct.Kind.BULK, CatalogProduct.Unit.ML, 100L);
    private final CatalogProduct split = product(CatalogProduct.Kind.SPLIT, CatalogProduct.Unit.ML, 200L);
    private final CatalogProduct water = product(CatalogProduct.Kind.BASE, CatalogProduct.Unit.ML, 10L);
    private final CatalogProduct atomizer = product(CatalogProduct.Kind.ATOMIZER, CatalogProduct.Unit.PCS, 500L);

    @BeforeEach
    void setUp() {
        service = new ReadyProductServiceImpl(productService, modelService, kindService);
        aroma.setId(UUID.randomUUID());
        aroma.setBrand("Бренд");
        aroma.setName("Аромат");
        oil.setModelId(aroma.getId());
        split.setModelId(aroma.getId());
        ProductKind ready = new ProductKind();
        ready.setName("Готовое");
        lenient().when(kindService.find("READY")).thenReturn(ready);
        lenient().when(modelService.get(aroma.getId())).thenReturn(aroma);
        lenient().when(productService.findByCompositionKey(anyString())).thenReturn(Optional.empty());
        lenient().when(productService.nextArticle()).thenReturn("R-1");
        lenient().when(productService.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void findOrCreate_createsDilutedBottleWithSummedPriceAndComponents() {
        givenCatalog(oil, water, atomizer);

        CatalogProduct ready = service.findOrCreate(List.of(
                new Part(oil.getId(), new BigDecimal("3")),
                new Part(water.getId(), new BigDecimal("7")),
                new Part(atomizer.getId(), BigDecimal.ONE)));

        assertThat(ready.getKind()).isEqualTo("READY");
        assertThat(ready.getUnit()).isEqualTo(CatalogProduct.Unit.PCS);
        assertThat(ready.getArticle()).isEqualTo("R-1");
        assertThat(ready.getModelId()).isEqualTo(aroma.getId());
        assertThat(ready.getLabel()).isEqualTo("разлив 10 мл · 30 %");
        assertThat(ready.getName()).isEqualTo("Бренд — Аромат · Готовое · разлив 10 мл · 30 %");
        assertThat(ready.getPriceMinor()).isEqualTo(3 * 100 + 7 * 10 + 500);
        assertThat(ready.getCompositionKey()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductComponent>> components = ArgumentCaptor.forClass(List.class);
        verify(productService).replaceComponents(eq(ready.getId()), components.capture());
        assertThat(components.getValue())
                .isSortedAccordingTo(Comparator.comparing(ProductComponent::getComponentId))
                .extracting(ProductComponent::getComponentId, ProductComponent::getQty)
                .containsExactlyInAnyOrder(
                        tuple(oil.getId(), new BigDecimal("3")),
                        tuple(water.getId(), new BigDecimal("7")),
                        tuple(atomizer.getId(), BigDecimal.ONE));
    }

    @Test
    void findOrCreate_labelsSplitByVolume() {
        givenCatalog(split, atomizer);

        CatalogProduct ready = service.findOrCreate(List.of(
                new Part(split.getId(), new BigDecimal("5")),
                new Part(atomizer.getId(), BigDecimal.ONE)));

        assertThat(ready.getLabel()).isEqualTo("распив 5 мл");
    }

    @Test
    void findOrCreate_returnsExistingProductOfSameComposition() {
        CatalogProduct existing = product(CatalogProduct.Kind.READY, CatalogProduct.Unit.PCS, 870L);
        when(productService.findByCompositionKey(anyString())).thenReturn(Optional.of(existing));

        CatalogProduct ready = service.findOrCreate(List.of(
                new Part(oil.getId(), new BigDecimal("1.5")),
                new Part(oil.getId(), new BigDecimal("1.5")),
                new Part(atomizer.getId(), BigDecimal.ONE)));

        assertThat(ready).isSameAs(existing);
        verify(productService, never()).save(any());
    }

    @Test
    void findOrCreate_rejectsEmptyComposition() {
        assertThatThrownBy(() -> service.findOrCreate(List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("без состава");
    }

    @Test
    void findOrCreate_rejectsUnknownProduct() {
        givenCatalog(oil);

        assertThatThrownBy(() -> service.findOrCreate(List.of(
                new Part(oil.getId(), BigDecimal.ONE), new Part(UUID.randomUUID(), BigDecimal.ONE))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("неизвестный товар");
    }

    @Test
    void findOrCreate_rejectsNestedComposite() {
        CatalogProduct kit = product(CatalogProduct.Kind.KIT, CatalogProduct.Unit.PCS, 0L);
        givenCatalog(oil, kit);

        assertThatThrownBy(() -> service.findOrCreate(List.of(
                new Part(oil.getId(), BigDecimal.ONE), new Part(kit.getId(), BigDecimal.ONE))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("не вкладывается");
    }

    @Test
    void findOrCreate_rejectsCompositionWithoutLiquid() {
        givenCatalog(water, atomizer);

        assertThatThrownBy(() -> service.findOrCreate(List.of(
                new Part(water.getId(), BigDecimal.TEN), new Part(atomizer.getId(), BigDecimal.ONE))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("масло или распив");
    }

    @Test
    void findOrCreate_rejectsCompositionWithoutAtomizer() {
        givenCatalog(oil);

        assertThatThrownBy(() -> service.findOrCreate(List.of(new Part(oil.getId(), BigDecimal.TEN))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Без атомайзера");
    }

    private void givenCatalog(CatalogProduct... products) {
        when(productService.findAllById(any())).thenReturn(List.of(products));
    }

    private static CatalogProduct product(
            CatalogProduct.Kind kind,
            CatalogProduct.Unit unit,
            Long priceMinor
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(UUID.randomUUID());
        p.setKind(kind.name());
        p.setUnit(unit);
        p.setPriceMinor(priceMinor);
        p.setName(kind.name());
        return p;
    }
}
