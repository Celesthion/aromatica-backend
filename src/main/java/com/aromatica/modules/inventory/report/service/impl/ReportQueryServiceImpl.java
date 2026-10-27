package com.aromatica.modules.inventory.report.service.impl;

import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.SalesCountRow;
import com.aromatica.modules.inventory.doc.model.SalesGroupRow;
import com.aromatica.modules.inventory.doc.service.SalesStatsService;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.DayRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.KindRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.ProductRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerRow;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerStats;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;
import com.aromatica.modules.inventory.report.service.ReportQueryService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportQueryServiceImpl implements ReportQueryService {

    static final int TOP_PRODUCTS = 20;
    /** Форма товара, которого уже нет в каталоге. */
    static final String UNKNOWN_KIND = "GOODS";

    SalesStatsService salesStats;
    ProductService productService;
    UserDirectory users;

    @Override
    public SellerStats sellerStats(String login) {
        UUID userId = users.idByLogin(login);
        var s = salesStats.sellerStats(userId);
        return new SellerStats(s.getSoldMinor(), s.getSalesCount(), s.getDaysWorked());
    }

    /** Продажи минус возвраты. Сворачивает база: в память приходит не больше «дней × товаров × продавцов» строк. */
    @Override
    public SummaryResponse summary(
            LocalDate from,
            LocalDate to
    ) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<SalesGroupRow> groups = salesStats.salesGroups(start, end);
        Map<UUID, CatalogProduct> products = productsOf(groups);

        PeriodTotals totals = new PeriodTotals(products);
        groups.forEach(totals::addGroup);
        salesStats.salesCounts(start, end).forEach(totals::addCount);

        return new SummaryResponse(totals.all.revenue, totals.all.cost, totals.all.profit(), (int) totals.all.sales,
                perDay(totals), topProducts(totals, products), perSeller(totals), perKind(totals, products),
                qtyString(totals.giftQtyX1000), totals.giftCost);
    }

    private Map<UUID, CatalogProduct> productsOf(List<SalesGroupRow> groups) {
        Map<UUID, CatalogProduct> products = new HashMap<>();
        productService.findAllById(groups.stream().map(SalesGroupRow::getProductId).distinct().toList())
                .forEach(p -> products.put(p.getId(), p));
        return products;
    }

    private static List<DayRow> perDay(PeriodTotals totals) {
        return totals.byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DayRow(e.getKey(), e.getValue().revenue, e.getValue().cost,
                        e.getValue().profit(), (int) e.getValue().sales))
                .toList();
    }

    private static List<ProductRow> topProducts(
            PeriodTotals totals,
            Map<UUID, CatalogProduct> products
    ) {
        return totals.byProduct.entrySet().stream()
                .map(e -> new ProductRow(e.getKey(),
                        products.containsKey(e.getKey()) ? products.get(e.getKey()).getName() : e.getKey().toString(),
                        qtyString(e.getValue().qtyX1000), e.getValue().revenue, e.getValue().profit()))
                .sorted(Comparator.comparingLong(ProductRow::revenueMinor).reversed())
                .limit(TOP_PRODUCTS)
                .toList();
    }

    private static List<KindRow> perKind(
            PeriodTotals totals,
            Map<UUID, CatalogProduct> products
    ) {
        Map<String, Sums> byKind = new HashMap<>();
        totals.byProduct.forEach((productId, sums) -> {
            String kind = products.containsKey(productId) ? products.get(productId).getKind() : UNKNOWN_KIND;
            byKind.computeIfAbsent(kind, k -> new Sums()).add(sums);
        });
        return byKind.entrySet().stream()
                .map(e -> new KindRow(e.getKey(), qtyString(e.getValue().qtyX1000),
                        e.getValue().revenue, e.getValue().profit()))
                .sorted(Comparator.comparingLong(KindRow::revenueMinor).reversed())
                .toList();
    }

    private List<SellerRow> perSeller(PeriodTotals totals) {
        Map<UUID, String> loginById = users.logins(totals.bySeller.keySet());
        Map<UUID, String> nameById = users.names(totals.bySeller.keySet());
        return totals.bySeller.entrySet().stream()
                .map(e -> new SellerRow(loginById.getOrDefault(e.getKey(), "?"),
                        nameById.getOrDefault(e.getKey(), "?"),
                        e.getValue().revenue, (int) e.getValue().sales))
                .sorted(Comparator.comparingLong(SellerRow::revenueMinor).reversed())
                .toList();
    }

    private static String qtyString(long qtyTimes1000) {
        return new BigDecimal(qtyTimes1000).movePointLeft(3).stripTrailingZeros().toPlainString();
    }

    private static final class Sums {
        long revenue;
        long cost;
        long qtyX1000;
        long sales;

        void add(
                long revenue,
                long cost,
                long qtyX1000
        ) {
            this.revenue += revenue;
            this.cost += cost;
            this.qtyX1000 += qtyX1000;
        }

        void add(Sums other) {
            add(other.revenue, other.cost, other.qtyX1000);
        }

        long profit() {
            return revenue - cost;
        }
    }

    private static final class PeriodTotals {
        final Map<UUID, CatalogProduct> products;
        final Sums all = new Sums();
        final Map<LocalDate, Sums> byDay = new HashMap<>();
        final Map<UUID, Sums> byProduct = new HashMap<>();
        /** Только продажи: показывает, кто сколько напродавал. */
        final Map<UUID, Sums> bySeller = new HashMap<>();
        long giftCost;
        long giftQtyX1000;

        PeriodTotals(Map<UUID, CatalogProduct> products) {
            this.products = products;
        }

        void addGroup(SalesGroupRow g) {
            int sign = DocType.RETURN.name().equals(g.getDocType()) ? -1 : 1;
            long revenue = sign * g.getRevenueMinor();
            long cost = sign * g.getCostMinor();
            long qtyX1000 = sign * g.getQty().movePointRight(3).longValueExact();
            all.add(revenue, cost, 0);
            byDay.computeIfAbsent(g.getDay(), d -> new Sums()).add(revenue, cost, 0);
            byProduct.computeIfAbsent(g.getProductId(), p -> new Sums()).add(revenue, cost, qtyX1000);
            if (g.getGift()) {
                giftCost += cost;
                giftQtyX1000 += giftQtyX1000(g, sign, qtyX1000);
            }
            if (sign > 0 && g.getSellerId() != null) {
                bySeller.computeIfAbsent(g.getSellerId(), s -> new Sums()).add(revenue, 0, 0);
            }
        }

        void addCount(SalesCountRow c) {
            all.sales += c.getSales();
            byDay.computeIfAbsent(c.getDay(), d -> new Sums()).sales += c.getSales();
            if (c.getSellerId() != null) {
                bySeller.computeIfAbsent(c.getSellerId(), s -> new Sums()).sales += c.getSales();
            }
        }

        /** KPI «Подарков»: наливная строка (пробник N мл) = 1 подарок, штучные — по количеству. */
        private long giftQtyX1000(
                SalesGroupRow g,
                int sign,
                long qtyX1000
        ) {
            CatalogProduct p = products.get(g.getProductId());
            return p != null && p.getUnit() == CatalogProduct.Unit.ML ? sign * g.getLineCount() * 1000 : qtyX1000;
        }
    }
}
