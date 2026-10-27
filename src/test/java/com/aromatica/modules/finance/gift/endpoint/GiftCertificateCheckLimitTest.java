package com.aromatica.modules.finance.gift.endpoint;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.web.RequestRateLimiter;
import com.aromatica.common.web.TooManyRequestsException;
import com.aromatica.modules.finance.gift.endpoint.impl.GiftCertificateEndpointImpl;
import com.aromatica.modules.finance.gift.mapper.GiftCertificateMapper;
import com.aromatica.modules.finance.gift.service.GiftCertificateSearchService;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.aromatica.security.auth.service.UserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Проверка кода сертификата с кассы ограничена по кассиру: перебором код не подобрать. */
@ExtendWith(MockitoExtension.class)
class GiftCertificateCheckLimitTest {

    @Mock GiftCertificateService service;
    @Mock GiftCertificateMapper mapper;
    @Mock UserDirectory users;
    @Mock CounterpartySearchService counterpartySearchService;
    @Mock PageMapper pageMapper;

    GiftCertificateEndpointImpl endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new GiftCertificateEndpointImpl(service, mock(GiftCertificateSearchService.class), mapper, users, counterpartySearchService, pageMapper,
                new RequestRateLimiter(Clock.systemUTC()));
        when(service.find(anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String login) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                login, "x", List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
    }

    @Test
    void afterLimit_nextCheckIsRejected() {
        loginAs("cashier");
        for (int i = 0; i < GiftCertificateEndpointImpl.CHECKS_PER_MINUTE; i++) {
            endpoint.check("X" + i);
        }
        assertThatThrownBy(() -> endpoint.check("X"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Слишком много проверок кода — подождите минуту");
    }

    @Test
    void check_limitIsPerCashier() {
        loginAs("first");
        for (int i = 0; i < GiftCertificateEndpointImpl.CHECKS_PER_MINUTE; i++) {
            endpoint.check("X" + i);
        }
        loginAs("second");
        assertThatCode(() -> endpoint.check("X")).doesNotThrowAnyException();
    }
}
