package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.impl.ProductEditServiceImpl;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Возврат товара из архива — по тем же правилам, что и создание. Данные синтетические. */
@ExtendWith(MockitoExtension.class)
class ProductArchiveTest {

    private static final UUID MODEL = UUID.randomUUID();

    @Mock
    private ProductService productService;
    @Mock
    private ProductModelService modelService;
    @Mock
    private ProductKindService kindService;

    private ProductEditService service;

    @BeforeEach
    void setUp() {
        service = new ProductEditServiceImpl(productService, modelService, kindService);
    }

    private CatalogProduct product(
            String kind,
            String label,
            boolean archived
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(UUID.randomUUID());
        p.setModelId(MODEL);
        p.setKind(kind);
        p.setLabel(label);
        p.setVolumeMl(new BigDecimal("100"));
        p.setArchived(archived);
        p.setUnit(CatalogProduct.Unit.PCS);
        lenient().when(productService.find(p.getId())).thenReturn(p);
        return p;
    }

    @Test
    void setArchived_restoreRejectsDuplicateOfActiveProduct() {
        CatalogProduct archived = product("BOTTLE", "Red", true);
        CatalogProduct active = product("BOTTLE", "red", false);
        when(productService.findByModelId(MODEL)).thenReturn(List.of(archived, active));

        assertThatThrownBy(() -> service.setArchived(archived.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("уже есть");
        assertThat(archived.isArchived()).isTrue();
    }

    @Test
    void setArchived_restoreRejectsSecondActiveBase() {
        CatalogProduct archived = product("BASE", null, true);
        CatalogProduct activeBase = product("BASE", "другая", false);
        activeBase.setModelId(UUID.randomUUID());
        when(productService.findByModelId(MODEL)).thenReturn(List.of(archived));
        when(productService.findActiveByKind("BASE")).thenReturn(List.of(activeBase));

        assertThatThrownBy(() -> service.setArchived(archived.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Парфюмерная вода");
        assertThat(archived.isArchived()).isTrue();
    }

    @Test
    void setArchived_restoresUniqueProduct() {
        CatalogProduct archived = product("BOTTLE", "Red", true);
        CatalogProduct other = product("BOTTLE", "Blue", false);
        when(productService.findByModelId(MODEL)).thenReturn(List.of(archived, other));

        service.setArchived(archived.getId(), false);

        assertThat(archived.isArchived()).isFalse();
    }

    /** Комплект с составляющей в архиве вернулся бы несобираемым. */
    @Test
    void setArchived_restoreKitRejectsArchivedComponent() {
        CatalogProduct kit = product("KIT", "Набор", true);
        CatalogProduct part = product("BOTTLE", "Red", true);
        ProductComponent line = new ProductComponent();
        line.setProductId(kit.getId());
        line.setComponentId(part.getId());
        line.setQty(BigDecimal.ONE);
        when(productService.findByModelId(MODEL)).thenReturn(List.of(kit, part));
        when(productService.componentsOf(List.of(kit.getId()))).thenReturn(Map.of(kit.getId(), List.of(line)));
        when(productService.findAllById(List.of(part.getId()))).thenReturn(List.of(part));

        assertThatThrownBy(() -> service.setArchived(kit.getId(), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("в архиве");
        assertThat(kit.isArchived()).isTrue();
    }
}
