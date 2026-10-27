package com.aromatica.modules.finance.gift.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.common.web.RequestRateLimiter;
import com.aromatica.common.web.TooManyRequestsException;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.BlockRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.CertificateCheck;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GenerateBatchRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftStats;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.UseDto;
import com.aromatica.modules.finance.gift.endpoint.GiftCertificateEndpoint;
import com.aromatica.modules.finance.gift.mapper.GiftCertificateMapper;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.search.GiftCertificateSearchRequest;
import com.aromatica.modules.finance.gift.service.GiftCertificateSearchService;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Продажа бланка живёт в SaleEndpoint: ей нужна смена, а модуль сертификатов про смены не знает. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificateEndpointImpl implements GiftCertificateEndpoint {

    /** На кассира в минуту: защита от перебора кодов. */
    public static final int CHECKS_PER_MINUTE = 30;

    /** Покупателей под строку поиска: больше совпадений почти не бывает. */
    static final int BUYER_MATCH_LIMIT = 200;

    GiftCertificateService service;
    GiftCertificateSearchService searchService;
    GiftCertificateMapper mapper;
    UserDirectory users;
    CounterpartySearchService counterpartySearchService;
    PageMapper pageMapper;
    RequestRateLimiter rateLimiter;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<GiftCertificateDto> search(GiftCertificateSearchRequest request) {
        // потерявший бланк клиент помнит только себя, поэтому ищем и по покупателю
        List<UUID> buyerIds = List.of();
        if (request.getQ() != null && !request.getQ().isBlank()) {
            CounterpartySearchRequest byBuyer = new CounterpartySearchRequest();
            byBuyer.setQ(request.getQ());
            byBuyer.setType(Counterparty.Type.CUSTOMER);
            byBuyer.setIncludeArchived(true);
            byBuyer.setSize(BUYER_MATCH_LIMIT);
            buyerIds = counterpartySearchService.search(byBuyer).getContent().stream()
                    .map(Counterparty::getId).toList();
        }
        Page<GiftCertificate> page = searchService.search(request, buyerIds);
        return pageMapper.toResponse(page, mapper.toDtos(page.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public GiftStats stats() {
        return mapper.toStats(service.stats());
    }

    @Override
    @Transactional
    public List<GiftCertificateDto> generateBatch(GenerateBatchRequest request) {
        UUID userId = users.idByLogin(CurrentUser.login());
        return mapper.toDtos(
                service.generateBatch(request.count(), request.nominalMinor(), request.comment(), userId));
    }

    @Override
    @Transactional
    public GiftCertificateDto block(
            UUID id,
            BlockRequest request
    ) {
        return mapper.toDto(service.block(id, request.comment(), users.idByLogin(CurrentUser.login())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UseDto> history(UUID id) {
        return service.history(id).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateCheck check(String code) {
        if (!rateLimiter.tryAcquire("gift-check " + CurrentUser.login(), CHECKS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new TooManyRequestsException("Слишком много проверок кода — подождите минуту", 60);
        }
        // «кода нет» для кассира — обычный ответ, а не ошибка
        return mapper.toCheck(service.find(code).orElse(null), code);
    }
}
