package com.aromatica.common.web;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.config.SecurityConfig;
import com.aromatica.config.TimeConfig;
import com.aromatica.modules.finance.gift.controller.GiftCertificateController;
import com.aromatica.modules.finance.gift.dtos.GiftDtos;
import com.aromatica.modules.finance.gift.endpoint.GiftCertificateEndpoint;
import com.aromatica.modules.pos.reservation.controller.ReservationController;
import com.aromatica.modules.pos.reservation.endpoint.ReservationEndpoint;
import com.aromatica.modules.pos.sale.controller.SaleController;
import com.aromatica.modules.pos.sale.endpoint.SaleEndpoint;
import com.aromatica.modules.pos.shift.controller.ShiftController;
import com.aromatica.modules.pos.shift.endpoint.ShiftEndpoint;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import com.aromatica.modules.pos.storefront.controller.StorefrontController;
import com.aromatica.modules.pos.storefront.endpoint.StorefrontEndpoint;
import com.aromatica.security.auth.authentication.ApiAuthEntryPoint;
import com.aromatica.security.auth.authentication.AppUserDetailsService;
import com.aromatica.security.auth.authentication.JwtAuthenticationFilter;
import com.aromatica.security.auth.authentication.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт HTTP-слоя: статусы и тексты ошибок, роли, Bean Validation.
 *
 * Сервисы замоканы, проверяется только то, что лежит между HTTP и сервисом:
 * {@link ApiExceptionHandler}, {@code @Valid} на контроллерах, {@code @PreAuthorize}
 * и базовые правила доступа в {@link SecurityConfig}. Без этих тестов, например,
 * пропущенный параметр мог незаметно отдавать 500.
 */
@WebMvcTest(controllers = {ShiftController.class, SaleController.class, StorefrontController.class,
        GiftCertificateController.class, ReservationController.class})
@Import({SecurityConfig.class, TimeConfig.class, JwtAuthenticationFilter.class, ApiAuthEntryPoint.class,
        RequestRateLimiter.class})
class ApiContractTest {

    /** id товара в теле запроса — UUID, как его присылает фронт. */
    private static final String PRODUCT_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    /** Цепочку фильтров Security подключаем руками, чтобы @WithMockUser доходил до запроса независимо от автонастройки слайса. */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @MockitoBean ShiftEndpoint shiftEndpoint;
    @MockitoBean SaleEndpoint saleEndpoint;
    @MockitoBean StorefrontEndpoint storefrontEndpoint;
    @MockitoBean GiftCertificateEndpoint giftEndpoint;
    @MockitoBean ReservationEndpoint reservationEndpoint;
    // эти двое нужны фильтру JWT; в тестах вход через @WithMockUser, токены не разбираются
    @MockitoBean JwtService jwtService;
    @MockitoBean AppUserDetailsService userDetailsService;

    private static final String SHIFT = UUID.randomUUID().toString();

    @Test
    @WithMockUser(roles = "SELLER")
    void missingRequestParam_is400WithParamName() throws Exception {
        mvc.perform(get("/api/pos/gift-certificates/check"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Не передан обязательный параметр: code"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void unknownRoute_is404() throws Exception {
        mvc.perform(get("/api/pos/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Ресурс не найден"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void wrongMethod_is405() throws Exception {
        mvc.perform(post("/api/pos/shifts/open"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Метод не поддерживается"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void notFoundFromService_is404WithMessage() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new NotFoundException("Сертификат не найден: X"));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Сертификат не найден: X"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void conflictFromService_is409() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new ConflictException("Бланк заблокирован"));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Бланк заблокирован"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void businessRuleFromService_is400() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new BusinessRuleException("Сумма не может быть нулевой"));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Сумма не может быть нулевой"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void jdkExceptionFromDeep_is500WithoutInternals() throws Exception {
        // IllegalStateException бросают Spring, Hibernate и JDK: это сбой, а не отказ по правилам,
        // и его служебный текст клиенту не показываем
        when(giftEndpoint.check(anyString())).thenThrow(new IllegalStateException("Session/EntityManager is closed"));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Внутренняя ошибка"));
    }

    /**
     * Отказ базы объясняем по делу: ссылка на удалённую запись означает не
     * «нарушена уникальность», а «обновите страницу».
     */
    @Test
    @WithMockUser(roles = "SELLER")
    void foreignKeyViolation_is409WithOwnMessage() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new DataIntegrityViolationException(
                "insert or update violates foreign key constraint",
                new SQLException("fk", "23503")));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Запись ссылается на то, чего уже нет — обновите страницу"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void numericOverflow_is400() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new DataIntegrityViolationException(
                "numeric field overflow", new SQLException("overflow", "22003")));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Значение слишком большое"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void uniqueViolation_keepsUniquenessMessage() throws Exception {
        when(giftEndpoint.check(anyString())).thenThrow(new DataIntegrityViolationException(
                "duplicate key", new SQLException("dup", "23505")));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Конфликт данных — проверьте уникальность значений"));
    }

    /**
     * Списание хвоста разлива доступно обычному продавцу: потолка у операции нет,
     * её сдерживает видимость в сводке смены и ленте, а не запрет. Если метод
     * закроют ролью, тест это заметит.
     */
    @Test
    @WithMockUser(roles = "SELLER")
    void splitWriteOff_allowedForSeller() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/split-writeoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":20,\"pouredMl\":80}"))
                .andExpect(status().isOk());
        verify(saleEndpoint).writeOffSplit(any(), any());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void splitWriteOffWithZeroQty_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/split-writeoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Объём списания должен быть больше нуля"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void receiptWithoutLines_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[],\"cashMinor\":0,\"noCashMinor\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Чек без строк пробить нельзя"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void receiptLineWithNegativePrice_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1,\"priceMinor\":-5}],"
                                + "\"cashMinor\":0,\"noCashMinor\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Цена не может быть отрицательной"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void unknownEnumValue_is400WithFieldAndAllowedValues() throws Exception {
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"SIDEWAYS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Недопустимое значение поля direction: SIDEWAYS (допустимо: ASC, DESC)"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void receiptWithNegativeCash_is400() throws Exception {
        // −5000 наличными и 6000 переводом в сумме сходятся с чеком, но из ящика ушли бы деньги
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1,\"priceMinor\":1000}],"
                                + "\"cashMinor\":-5000,\"noCashMinor\":6000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Сумма оплаты не может быть отрицательной"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void returnWithNegativeNoCash_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceReceiptId\":\"" + UUID.randomUUID() + "\","
                                + "\"lines\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1,\"priceMinor\":1000}],"
                                + "\"noCashMinor\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Сумма оплаты не может быть отрицательной"));
    }

    @Test
    @WithMockUser(roles = "SENIOR_SELLER")
    void openShiftWithNegativeCash_is400() throws Exception {
        mvc.perform(post("/api/pos/shift/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCashMinor\":-100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Размен не может быть отрицательным"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void closeShiftWithNegativeCount_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedCashMinor\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Пересчитанная сумма не может быть отрицательной"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void sellCertificateWithoutBuyer_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/" + SHIFT + "/gift-certificates/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GC-1\",\"cashMinor\":500000,\"noCashMinor\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Не указан покупатель сертификата"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateBatchWithZeroCount_is400() throws Exception {
        mvc.perform(post("/api/admin/gift-certificates/batch/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":0,\"nominalMinor\":500000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Сколько бланков — минимум 1"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void reservationWithoutCustomer_is400() throws Exception {
        mvc.perform(post("/api/pos/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + UUID.randomUUID() + "\","
                                + "\"lines\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1,\"priceMinor\":100}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Выберите покупателя"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void reservationLineWithNegativePrice_is400() throws Exception {
        mvc.perform(post("/api/pos/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"counterpartyId\":\"" + UUID.randomUUID() + "\","
                                + "\"lines\":[{\"productId\":\"" + PRODUCT_ID + "\",\"qty\":1,\"priceMinor\":-1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Цена в строке брони не может быть отрицательной"));
    }

    @Test
    void anonymous_is401() throws Exception {
        mvc.perform(get("/api/pos/shifts/open")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_cannotEnterAdmin_403() throws Exception {
        mvc.perform(get("/api/admin/gift-certificates/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_cannotOpenShift_403() throws Exception {
        mvc.perform(post("/api/pos/shift/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_readsStats_200() throws Exception {
        when(giftEndpoint.stats()).thenReturn(new GiftDtos.GiftStats(0, 0, 0));
        mvc.perform(get("/api/admin/gift-certificates/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void tooManyRequests_is429WithRetryAfter() throws Exception {
        when(giftEndpoint.check(anyString()))
                .thenThrow(new TooManyRequestsException("Слишком много проверок кода — подождите минуту", 60));
        mvc.perform(get("/api/pos/gift-certificates/check").param("code", "X"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("Слишком много проверок кода — подождите минуту"));
    }

    private ShiftSearchRequest capturedShiftSearch() {
        ArgumentCaptor<ShiftSearchRequest> captor = ArgumentCaptor.forClass(ShiftSearchRequest.class);
        verify(shiftEndpoint).searchShifts(captor.capture());
        return captor.getValue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_readsParamsFromBody() throws Exception {
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\":\"12\",\"page\":2,\"size\":5}"))
                .andExpect(status().isOk());
        ShiftSearchRequest r = capturedShiftSearch();
        assertThat(r.getQ()).isEqualTo("12");
        assertThat(r.getPage()).isEqualTo(2);
        assertThat(r.getSize()).isEqualTo(5);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_nullDirection_fallsBackToDefault() throws Exception {
        // явный null затирает значение по умолчанию; раньше сортировка падала с NPE и отдавала 500
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":null}"))
                .andExpect(status().isOk());
        ShiftSearchRequest r = capturedShiftSearch();
        assertThat(r.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(r.getSortBy().getSorting(r.getDirection())).isNotNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_brokenJson_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Некорректный запрос"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_unknownSort_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortBy\":\"FOO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_wrongParamType_is400() throws Exception {
        mvc.perform(post("/api/pos/shifts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void search_byGet_is405() throws Exception {
        mvc.perform(get("/api/pos/shifts/search")).andExpect(status().isMethodNotAllowed());
    }
}
