package com.aromatica.modules.inventory.report.dtos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO отчётов по продажам. */
public final class ReportDtos {

    private ReportDtos() {}

    public record DayRow(
            LocalDate date,
            long revenueMinor,
            long costMinor,
            long profitMinor,
            int salesCount
    ) {}

    public record ProductRow(
            UUID productId,
            String productName,
            String qty,
            long revenueMinor,
            long profitMinor
    ) {}

    public record SellerRow(
            String login,
            String displayName,
            long revenueMinor,
            int salesCount
    ) {}

    public record KindRow(
            String kind,
            String qty,
            long revenueMinor,
            long profitMinor
    ) {}

    public record SummaryResponse(
            long revenueMinor,
            long costMinor,
            long profitMinor,
            int salesCount,
            List<DayRow> perDay,
            List<ProductRow> topProducts,
            List<SellerRow> perSeller,
            List<KindRow> perKind,
            String giftQty,
            long giftCostMinor
    ) {}

    public record SellerStats(
            long soldMinor,
            long salesCount,
            long daysWorked
    ) {}
}
