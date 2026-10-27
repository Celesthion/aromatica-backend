package com.aromatica.modules.finance.gift.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.BlockRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GenerateBatchRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftStats;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.UseDto;
import com.aromatica.modules.finance.gift.endpoint.GiftCertificateEndpoint;
import com.aromatica.modules.finance.gift.search.GiftCertificateSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Сертификаты", description = "Сертификаты в админке: завоз бланков и блокировка. Продажа и погашение — на кассе.")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/gift-certificates")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificateController {

    GiftCertificateEndpoint giftCertificateEndpoint;

    @Operation(summary = "Поиск сертификатов", description = "Фильтр: код, покупатель, статус.")
    @PostMapping("/search")
    public PageResponse<GiftCertificateDto> search(@RequestBody GiftCertificateSearchRequest request) {
        return giftCertificateEndpoint.search(request);
    }

    @Operation(summary = "Сводка по сертификатам")
    @GetMapping("/stats")
    public GiftStats stats() {
        return giftCertificateEndpoint.stats();
    }

    @Operation(summary = "Сгенерировать партию сертификатов", description = "Коды генерирует система; бланки затем печатаются по реестру.")
    @PostMapping("/batch/generate")
    public List<GiftCertificateDto> generateBatch(@Valid @RequestBody GenerateBatchRequest request) {
        return giftCertificateEndpoint.generateBatch(request);
    }

    @Operation(summary = "Заблокировать сертификат", description = "Для потерянного или испорченного бланка.")
    @PostMapping("/{id}/block")
    public GiftCertificateDto block(
            @PathVariable UUID id,
            @Valid @RequestBody BlockRequest request
    ) {
        return giftCertificateEndpoint.block(id, request);
    }

    @Operation(summary = "История использования сертификата")
    @GetMapping("/{id}/history")
    public List<UseDto> history(@PathVariable UUID id) {
        return giftCertificateEndpoint.history(id);
    }
}
