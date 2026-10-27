package com.aromatica.modules.inventory.report.controller;

import com.aromatica.modules.inventory.report.dtos.ReportDtos.SellerStats;
import com.aromatica.modules.inventory.report.dtos.ReportDtos.SummaryResponse;
import com.aromatica.modules.inventory.report.endpoint.ReportEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Отчёты", description = "Отчёты по продажам.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SENIOR_SELLER')")
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportController {

    ReportEndpoint reportEndpoint;

    @Operation(summary = "Статистика продавца", description = "По логину продавца, за всё время.")
    @GetMapping("/seller/{login}")
    public SellerStats sellerStats(@PathVariable String login) {
        return reportEndpoint.sellerStats(login);
    }

    @Operation(summary = "Сводка за период")
    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportEndpoint.summary(from, to);
    }
}
