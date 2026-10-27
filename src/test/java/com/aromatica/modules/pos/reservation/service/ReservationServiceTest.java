package com.aromatica.modules.pos.reservation.service;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.CreateReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationLineRequest;
import com.aromatica.modules.pos.reservation.mapper.ReservationMapper;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.model.ReservationLine;
import com.aromatica.modules.pos.reservation.model.ReservedQtyRow;
import com.aromatica.modules.pos.reservation.repository.ReservationRepository;
import com.aromatica.modules.pos.reservation.service.impl.ReservationServiceImpl;
import com.aromatica.modules.pos.storefront.service.PosCatalogQueryService;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Бронь товара, мягкий резерв остатков. Данные синтетические (test-aroma-N), не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private StockBalanceService stockBalanceService;
    @Mock
    private SettingService settingService;
    @Mock
    private ProductService productService;
    @Mock
    private CounterpartyService counterpartyService;
    @Mock
    private UserDirectory users;
    private AppUser seller;
    private static final UUID CUSTOMER = UUID.randomUUID();

    private ReservationService service;
    private ReservationMapper mapper;

    /** Запрос проходит через тот же маппер, что в endpoint. */
    private Reservation create(
            UUID warehouseId,
            UUID counterpartyId,
            String comment,
            Integer days,
            List<ReservationLineRequest> lines
    ) {
        return service.create(mapper.toEntity(new CreateReservationRequest(warehouseId, counterpartyId, comment,
                days, lines)), days);
    }

    @BeforeEach
    void setUp() {
        service = new ReservationServiceImpl(reservationRepository, stockBalanceService,
                settingService, productService, counterpartyService,
                new SellerDiscountCap(settingService, productService), users, Clock.systemDefaultZone());
        mapper = new ReservationMapper(Clock.systemDefaultZone(), mock(PosCatalogQueryService.class), productService);
        // продавец, закреплённый за тестовым складом
        seller = new AppUser();
        seller.setRole(AppUser.Role.SELLER);
        seller.getWarehouseIds().add(WAREHOUSE);
        lenient().when(users.require("seller")).thenReturn(seller);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "seller", "x", List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
        // покупатель брони — синтетическая карточка
        Counterparty customer = new Counterparty();
        customer.setId(CUSTOMER);
        customer.setName("Тест Покупатель");
        customer.setPhone("+996 555 000000");
        lenient().when(counterpartyService.find(CUSTOMER)).thenReturn(customer);
        // настройки как в сиде: по умолчанию 3 дня, потолок 14
        lenient().when(settingService.getInt(eq(SettingService.RESERVATION_DEFAULT_DAYS), anyInt()))
                .thenReturn(3);
        lenient().when(settingService.getInt(eq(SettingService.RESERVATION_MAX_DAYS), anyInt()))
                .thenReturn(14);
        // других броней нет, save возвращает сущность как есть
        lenient().when(reservationRepository.findByWarehouseIdAndStatusInOrderByCreatedAtDesc(
                eq(WAREHOUSE), any())).thenReturn(List.of());
        lenient().when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // суммы резерва — как запрос sumReserved: действующие брони склада (ACTIVE и срок не вышел)
        lenient().when(reservationRepository.sumReserved(eq(WAREHOUSE), any(), eq(Reservation.Status.ACTIVE), any()))
                .thenAnswer(inv -> reservedRows(inv.getArgument(1), null));
        lenient().when(reservationRepository.sumReservedExcept(
                        eq(WAREHOUSE), any(), eq(Reservation.Status.ACTIVE), any(), any()))
                .thenAnswer(inv -> reservedRows(inv.getArgument(1), inv.getArgument(4)));
    }

    /** Брони склада, которые видит запрос резерва. */
    private final List<Reservation> existing = new ArrayList<>();

    private List<ReservedQtyRow> reservedRows(
            Collection<UUID> productIds,
            UUID exceptId
    ) {
        Map<UUID, BigDecimal> sums = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        existing.stream()
                .filter(r -> r.isReserving(now) && !r.getId().equals(exceptId))
                .flatMap(r -> r.getLines().stream())
                .filter(l -> productIds.contains(l.getProductId()))
                .forEach(l -> sums.merge(l.getProductId(), l.getQty(), BigDecimal::add));
        return sums.entrySet().stream().<ReservedQtyRow>map(e -> new Reserved(e.getKey(), e.getValue())).toList();
    }

    /** Строка запроса резерва. */
    private record Reserved(
            UUID productId,
            BigDecimal qty
    ) implements ReservedQtyRow {

        @Override
        public UUID getProductId() {
            return productId;
        }

        @Override
        public BigDecimal getQty() {
            return qty;
        }
    }

    private BigDecimal reserved(UUID productId) {
        return service.availability(WAREHOUSE, List.of(productId), null).get(productId).reserved();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void givenStock(
            UUID productId,
            String qty
    ) {
        lenient().when(stockBalanceService.qtyMap(eq(WAREHOUSE), any()))
                .thenReturn(Map.of(productId, new BigDecimal(qty)));
    }

    /** Активная бронь склада; expiresInDays < 0 — просроченная. */
    private Reservation reservation(
            UUID productId,
            String qty,
            int expiresInDays
    ) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setWarehouseId(WAREHOUSE);
        r.setCustomerName("Тест Покупатель");
        r.setStatus(Reservation.Status.ACTIVE);
        r.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        ReservationLine line = new ReservationLine();
        line.setId(UUID.randomUUID());
        line.setReservation(r);
        line.setProductId(productId);
        line.setQty(new BigDecimal(qty));
        line.setPriceMinor(100_000);
        line.setLineNo(1);
        r.getLines().add(line);
        return r;
    }

    private void givenReservations(Reservation... reservations) {
        existing.addAll(List.of(reservations));
        lenient().when(reservationRepository.findByWarehouseIdAndStatusInOrderByCreatedAtDesc(
                eq(WAREHOUSE), any())).thenReturn(List.of(reservations));
    }

    private static List<ReservationLineRequest> lines(
            UUID productId,
            String qty
    ) {
        return List.of(new ReservationLineRequest(productId, new BigDecimal(qty), 100_000));
    }

    @Test
    void create_reservesStockAndFixesPrice() {
        givenStock(id("test-aroma-1"), "5");

        Reservation created = create(WAREHOUSE, CUSTOMER,
                "до пятницы", null, lines(id("test-aroma-1"), "2"));

        assertThat(created.getStatus()).isEqualTo(Reservation.Status.ACTIVE);
        // days=null: срок берётся из default_days, 3 дня от сегодня
        assertThat(created.getExpiresAt())
                .isCloseTo(LocalDateTime.now().plusDays(3), within(Duration.ofMinutes(1)));
        assertThat(created.getLines()).hasSize(1);
        assertThat(created.getLines().getFirst().getPriceMinor()).isEqualTo(100_000);
        verify(reservationRepository).save(created);

        // созданная бронь сразу резервирует
        givenReservations(created);
        assertThat(reserved(id("test-aroma-1"))).isEqualByComparingTo("2");
    }

    @Test
    void create_rejectsSellerPriceBelowDiscountCap() {
        // каталог 1000 с, потолок 20%: продавцу бронь по 700 с не оформить, иначе касса
        // потом продаст по ней как по обещанной цене
        givenStock(id("test-aroma-1"), "5");
        CatalogProduct product = new CatalogProduct();
        product.setId(id("test-aroma-1"));
        product.setPriceMinor(100_000L);
        when(productService.findAllById(any())).thenReturn(List.of(product));
        when(settingService.getInt(eq(SettingService.MAX_SELLER_DISCOUNT_PCT), anyInt())).thenReturn(20);

        assertThatThrownBy(() -> create(WAREHOUSE, CUSTOMER, null, null,
                List.of(new ReservationLineRequest(id("test-aroma-1"), BigDecimal.ONE, 70_000))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("администратор");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_locksStockBeforeCheckingAvailability() {
        givenStock(id("test-aroma-1"), "5");

        create(WAREHOUSE, CUSTOMER, null, null, lines(id("test-aroma-1"), "1"));

        InOrder order = inOrder(stockBalanceService);
        order.verify(stockBalanceService).lockAll(eq(WAREHOUSE), eq(List.of(id("test-aroma-1"))));
        order.verify(stockBalanceService).qtyMap(eq(WAREHOUSE), any());
    }

    @Test
    void sellerOfOtherWarehouse_cannotSeeOrTouchReservations() {
        seller.getWarehouseIds().clear();
        Reservation foreign = reservation(id("test-aroma-1"), "1", 3);
        lenient().when(reservationRepository.require(eq(foreign.getId()), anyString())).thenReturn(foreign);

        assertThatThrownBy(() -> service.list(WAREHOUSE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(foreign.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.extend(foreign.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> create(WAREHOUSE, CUSTOMER, null, null, lines(id("test-aroma-1"), "1")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(foreign.getStatus()).isEqualTo(Reservation.Status.ACTIVE);
    }

    @Test
    void create_daysAboveMaxClampedToCeiling() {
        givenStock(id("test-aroma-1"), "5");

        Reservation created = create(WAREHOUSE, CUSTOMER, null,
                100, lines(id("test-aroma-1"), "1"));

        assertThat(created.getExpiresAt())
                .isCloseTo(LocalDateTime.now().plusDays(14), within(Duration.ofMinutes(1)));
    }

    @Test
    void create_rejectsWhenOtherReservationAteStock() {
        // остаток 2, но другая активная бронь уже держит 2, доступно 0
        givenStock(id("test-aroma-1"), "2");
        givenReservations(reservation(id("test-aroma-1"), "2", 3));

        assertThatThrownBy(() -> create(WAREHOUSE, CUSTOMER, null,
                null, lines(id("test-aroma-1"), "1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("доступно");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_expiredReservationDoesNotBlock() {
        // просроченная бронь больше не резервирует, остаток снова доступен
        givenStock(id("test-aroma-1"), "2");
        givenReservations(reservation(id("test-aroma-1"), "2", -1));

        Reservation created = create(WAREHOUSE, CUSTOMER, null,
                null, lines(id("test-aroma-1"), "2"));

        assertThat(created.getLines().getFirst().getQty()).isEqualByComparingTo("2");
    }

    @Test
    void availability_isStockMinusReservesOfOthers() {
        givenStock(id("test-aroma-1"), "5");
        Reservation selling = reservation(id("test-aroma-1"), "1", 3);
        givenReservations(
                reservation(id("test-aroma-1"), "2", 3),
                selling,
                reservation(id("test-aroma-2"), "1", -1)); // просрочена, не резервирует

        var all = service.availability(WAREHOUSE, List.of(id("test-aroma-1"), id("test-aroma-2")), null);
        var exceptSelling = service.availability(WAREHOUSE, List.of(id("test-aroma-1")), selling.getId());

        assertThat(all.get(id("test-aroma-1")).available()).isEqualByComparingTo("2");
        // товара без остатка нет в qtyMap, но в ответе он есть — с нулём
        assertThat(all.get(id("test-aroma-2")).onHand()).isEqualByComparingTo("0");
        assertThat(all.get(id("test-aroma-2")).reserved()).isEqualByComparingTo("0");
        // резерв продаваемой брони её продаже не мешает
        assertThat(exceptSelling.get(id("test-aroma-1")).available()).isEqualByComparingTo("3");
    }

    @Test
    void extend_restoresReserveOfExpiredReservation() {
        Reservation expired = reservation(id("test-aroma-1"), "2", -1);
        when(reservationRepository.require(eq(expired.getId()), anyString())).thenReturn(expired);

        Reservation extended = service.extend(expired.getId(), 5);

        // срок считается от сегодня, а не от старого expires_at
        assertThat(extended.getExpiresAt())
                .isCloseTo(LocalDateTime.now().plusDays(5), within(Duration.ofMinutes(1)));
        givenReservations(extended);
        assertThat(reserved(id("test-aroma-1"))).isEqualByComparingTo("2");
    }

    /** Закрытая задачей бронь (EXPIRED) продлевается так же и снова становится действующей. */
    @Test
    void extend_reopensClosedExpiredReservation() {
        Reservation closed = reservation(id("test-aroma-1"), "2", -1);
        closed.setStatus(Reservation.Status.EXPIRED);
        when(reservationRepository.require(eq(closed.getId()), anyString())).thenReturn(closed);

        Reservation extended = service.extend(closed.getId(), 2);

        assertThat(extended.getStatus()).isEqualTo(Reservation.Status.ACTIVE);
        assertThat(extended.isExpired(LocalDateTime.now())).isFalse();
    }

    @Test
    void closedExpiredReservation_canBeCancelledButNotSold() {
        Reservation closed = reservation(id("test-aroma-1"), "1", -1);
        closed.setStatus(Reservation.Status.EXPIRED);
        when(reservationRepository.findByIdForUpdate(closed.getId())).thenReturn(Optional.of(closed));
        when(reservationRepository.require(eq(closed.getId()), anyString())).thenReturn(closed);

        assertThatThrownBy(() -> service.requireActiveForSale(closed.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("просрочена");
        service.cancel(closed.getId());
        assertThat(closed.getStatus()).isEqualTo(Reservation.Status.CANCELLED);
    }

    @Test
    void expireOverdue_closesActiveWithPassedTermInOneQuery() {
        when(reservationRepository.expireOverdue(
                eq(Reservation.Status.ACTIVE), eq(Reservation.Status.EXPIRED), any())).thenReturn(2);

        assertThat(service.expireOverdue()).isEqualTo(2);
    }

    @Test
    void extend_addsDaysToRemainingTermOfActiveReservation() {
        // броне осталось 10 дней; «+3 дн.» должно дать 13, а не срезать до 3
        Reservation active = reservation(id("test-aroma-1"), "1", 10);
        when(reservationRepository.require(eq(active.getId()), anyString())).thenReturn(active);
        LocalDateTime before = active.getExpiresAt();

        Reservation extended = service.extend(active.getId(), 3);

        assertThat(extended.getExpiresAt()).isEqualTo(before.plusDays(3));
    }

    @Test
    void cancel_releasesReserve() {
        Reservation active = reservation(id("test-aroma-1"), "2", 3);
        givenReservations(active);
        when(reservationRepository.require(eq(active.getId()), anyString())).thenReturn(active);

        service.cancel(active.getId());

        assertThat(active.getStatus()).isEqualTo(Reservation.Status.CANCELLED);
        // CANCELLED не попадает в выборку ACTIVE, резерв отпущен
        assertThat(reserved(id("test-aroma-1"))).isEqualByComparingTo("0");
    }

    @Test
    void markSold_closesReservationWithReceipt() {
        Reservation active = reservation(id("test-aroma-1"), "1", 3);
        when(reservationRepository.require(eq(active.getId()), anyString())).thenReturn(active);
        UUID docId = UUID.randomUUID();

        service.markSold(active.getId(), docId);

        assertThat(active.getStatus()).isEqualTo(Reservation.Status.SOLD);
        assertThat(active.getSoldDocId()).isEqualTo(docId);
    }

    @Test
    void markSold_rejectsClosedReservation() {
        Reservation sold = reservation(id("test-aroma-1"), "1", 3);
        sold.setStatus(Reservation.Status.SOLD);
        when(reservationRepository.require(eq(sold.getId()), anyString())).thenReturn(sold);

        assertThatThrownBy(() -> service.markSold(sold.getId(), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requireActiveForSale_rejectsExpiredAndMissing() {
        Reservation expired = reservation(id("test-aroma-1"), "1", -1);
        when(reservationRepository.findByIdForUpdate(expired.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.requireActiveForSale(expired.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("просрочена");
        assertThatThrownBy(() -> service.requireActiveForSale(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireActiveForSale_readsUnderRowLock() {
        // без блокировки два параллельных чека увидят бронь активной и оба её выкупят,
        // товар уйдёт дважды. Поэтому бронь читаем только через findByIdForUpdate
        Reservation active = reservation(id("test-aroma-1"), "1", 3);
        when(reservationRepository.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));

        assertThat(service.requireActiveForSale(active.getId())).isSameAs(active);
        verify(reservationRepository, never()).require(eq(active.getId()), anyString());
    }

    @Test
    void restoreAfterVoid_returnsReservationToCustomer() {
        // сторно значит «этого чека не было»: товар вернулся на полку, бронь должна вернуться
        // клиенту. Иначе он останется ни с чем, потому что закрытую бронь не отменить
        UUID docId = UUID.randomUUID();
        Reservation sold = reservation(id("test-aroma-1"), "1", 3);
        sold.setStatus(Reservation.Status.SOLD);
        sold.setSoldDocId(docId);
        when(reservationRepository.findBySoldDocId(docId)).thenReturn(Optional.of(sold));

        service.restoreAfterVoid(docId);

        assertThat(sold.getStatus()).isEqualTo(Reservation.Status.ACTIVE);
        assertThat(sold.getSoldDocId()).isNull();
        verify(reservationRepository).save(sold);
    }

    @Test
    void restoreAfterVoid_ignoresReceiptWithoutReservation() {
        UUID docId = UUID.randomUUID();
        when(reservationRepository.findBySoldDocId(docId)).thenReturn(Optional.empty());

        service.restoreAfterVoid(docId);

        verify(reservationRepository, never()).save(any());
    }
}
