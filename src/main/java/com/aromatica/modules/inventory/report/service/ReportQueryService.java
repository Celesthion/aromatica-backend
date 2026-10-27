package com.aromatica.modules.inventory.report.service;

import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerStats;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;

import java.time.LocalDate;

/**
 * Отчёт по проведённым продажам: себестоимость строк зафиксирована при проведении;
 * возвраты вычитаются, кроме разреза по продавцам.
 */
public interface ReportQueryService {

    SellerStats sellerStats(String login);

    SummaryResponse summary(LocalDate from, LocalDate to);
}
