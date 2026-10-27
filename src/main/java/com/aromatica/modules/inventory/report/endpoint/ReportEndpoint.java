package com.aromatica.modules.inventory.report.endpoint;

import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerStats;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;

import java.time.LocalDate;

/** Отчёты по продажам: DTO-слой. */
public interface ReportEndpoint {

    SellerStats sellerStats(String login);

    SummaryResponse summary(LocalDate from, LocalDate to);
}
