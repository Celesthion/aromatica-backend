package com.aromatica.modules.inventory.report.service;

import com.aromatica.modules.inventory.doc.model.SalesCountRow;
import com.aromatica.modules.inventory.doc.model.SalesGroupRow;
import com.aromatica.modules.inventory.doc.service.SalesStatsService;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.DayRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.KindRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.ProductRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;
import com.aromatica.modules.inventory.report.service.impl.ReportQueryServiceImpl;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.security.auth.service.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Сводка за период: продажи минус возвраты по дням, товарам, формам и продавцам.
 * Числа в ожиданиях посчитаны вручную по группам из setUp.
 */
@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 2);
    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BORIS = UUID.randomUUID();

    @Mock
    private SalesStatsService salesStats;
    @Mock
    private ProductService productService;
    @Mock
    private UserDirectory users;

    private final CatalogProduct bottle = product("Флакон", CatalogProduct.Kind.BOTTLE, CatalogProduct.Unit.PCS);
    private final CatalogProduct oil = product("Масло", CatalogProduct.Kind.BULK, CatalogProduct.Unit.ML);
    /** Товар, которого уже нет в каталоге: имя — его id, форма — GOODS. */
    private final UUID unknown = UUID.randomUUID();

    private SummaryResponse summary;

    @BeforeEach
    void setUp() {
        when(salesStats.salesGroups(any(), any())).thenReturn(List.of(
                new Group(DAY1, bottle.getId(), ANNA, "SALE", false, 12_000, 4_000, new BigDecimal("2"), 1),
                new Group(DAY1, oil.getId(), BORIS, "SALE", false, 5_000, 1_000, new BigDecimal("10.5"), 2),
                // наливной подарок: две строки пробников — два подарка, сколько бы мл ни налили
                new Group(DAY1, oil.getId(), ANNA, "SALE", true, 0, 300, new BigDecimal("3"), 2),
                new Group(DAY2, bottle.getId(), ANNA, "RETURN", false, 5_000, 2_000, new BigDecimal("1"), 1),
                new Group(DAY2, bottle.getId(), BORIS, "SALE", true, 0, 500, new BigDecimal("1"), 1),
                new Group(DAY2, unknown, null, "SALE", false, 700, 100, new BigDecimal("1"), 1)));
        when(salesStats.salesCounts(any(), any())).thenReturn(List.of(
                new Count(DAY1, ANNA, 1),
                new Count(DAY1, BORIS, 1),
                new Count(DAY2, BORIS, 2),
                new Count(DAY2, null, 1)));
        when(productService.findAllById(any())).thenReturn(List.of(bottle, oil));
        when(users.logins(any())).thenReturn(Map.of(ANNA, "anna", BORIS, "boris"));
        when(users.names(any())).thenReturn(Map.of(ANNA, "Анна", BORIS, "Борис"));

        summary = new ReportQueryServiceImpl(salesStats, productService, users).summary(DAY1, DAY2);
    }

    @Test
    void summary_returnsReduceRevenueAndCost() {
        assertThat(summary.revenueMinor()).isEqualTo(12_700);
        assertThat(summary.costMinor()).isEqualTo(3_900);
        assertThat(summary.profitMinor()).isEqualTo(8_800);
        assertThat(summary.salesCount()).isEqualTo(5);
    }

    @Test
    void summary_countsBulkGiftByLinesAndPieceGiftByQty() {
        assertThat(summary.giftQty()).isEqualTo("3");
        assertThat(summary.giftCostMinor()).isEqualTo(800);
    }

    @Test
    void summary_perDayIsSortedByDate() {
        assertThat(summary.perDay()).containsExactly(
                new DayRow(DAY1, 17_000, 5_300, 11_700, 2),
                new DayRow(DAY2, -4_300, -1_400, -2_900, 3));
    }

    @Test
    void summary_topProductsByRevenueWithUnknownProductNamedById() {
        assertThat(summary.topProducts()).containsExactly(
                new ProductRow(bottle.getId(), "Флакон", "2", 7_000, 4_500),
                new ProductRow(oil.getId(), "Масло", "13.5", 5_000, 3_700),
                new ProductRow(unknown, unknown.toString(), "1", 700, 600));
    }

    @Test
    void summary_perKindFoldsProductsAndUnknownGoesToGoods() {
        assertThat(summary.perKind()).containsExactly(
                new KindRow("BOTTLE", "2", 7_000, 4_500),
                new KindRow("BULK", "13.5", 5_000, 3_700),
                new KindRow("GOODS", "1", 700, 600));
    }

    @Test
    void summary_perSellerCountsOnlySalesOfKnownSellers() {
        assertThat(summary.perSeller()).containsExactly(
                new SellerRow("anna", "Анна", 12_000, 1),
                new SellerRow("boris", "Борис", 5_000, 3));
    }

    private static CatalogProduct product(
            String name,
            CatalogProduct.Kind kind,
            CatalogProduct.Unit unit
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setKind(kind.name());
        p.setUnit(unit);
        return p;
    }

    /** Компоненты названы как геттеры проекции — запись сама её реализует. */
    private record Group(
            LocalDate getDay,
            UUID getProductId,
            UUID getSellerId,
            String getDocType,
            boolean getGift,
            long getRevenueMinor,
            long getCostMinor,
            BigDecimal getQty,
            long getLineCount
    ) implements SalesGroupRow {}

    private record Count(
            LocalDate getDay,
            UUID getSellerId,
            long getSales
    ) implements SalesCountRow {}
}
