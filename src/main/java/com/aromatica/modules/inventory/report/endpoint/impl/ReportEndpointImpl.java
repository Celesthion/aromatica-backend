package com.aromatica.modules.inventory.report.endpoint.impl;

import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerStats;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;
import com.aromatica.modules.inventory.report.endpoint.ReportEndpoint;
import com.aromatica.modules.inventory.report.service.ReportQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportEndpointImpl implements ReportEndpoint {

    ReportQueryService reportQuery;

    @Override
    @Transactional(readOnly = true)
    public SellerStats sellerStats(String login) {
        return reportQuery.sellerStats(login);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse summary(
            LocalDate from,
            LocalDate to
    ) {
        return reportQuery.summary(from, to);
    }
}
