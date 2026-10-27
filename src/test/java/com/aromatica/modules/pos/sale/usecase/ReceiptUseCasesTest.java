package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.LineRequest;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.product.service.ReadyProductService;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.model.ReservationLine;
import com.aromatica.modules.pos.reservation.service.ReservationService.Availability;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosShelfRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReceiptRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReturnRequest;
import com.aromatica.modules.pos.sale.mapper.SaleCommandMapper;
import com.aromatica.modules.pos.sale.mapper.SaleCommandMapperImpl;
import com.aromatica.modules.pos.sale.service.BaseConsumption;
import com.aromatica.modules.pos.sale.service.ReceiptGiftGuard;
import com.aromatica.modules.pos.sale.service.ReceiptStockGuard;
import com.aromatica.modules.pos.sale.service.ShiftDocPoster;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.repository.PosShiftRepository;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.modules.pos.shift.service.impl.PosShiftServiceImpl;
import com.aromatica.modules.pos.shift.service.impl.ShiftSummaryQueryServiceImpl;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Чеки кассы: продажа, возврат, сторно. Данные синтетические (test-aroma-N), не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class ReceiptUseCasesTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private EntityManager entityManager;
    @Mock
    private PosShiftRepository shiftRepository;
    @Mock
    private DocService docService;
    @Mock
    private PostingService postingService;
    @Mock
    private MoneyService moneyService;
    @Mock
    private DocEventService docEventService;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private ProductService productService;
    /** Остаток склада смены и резерв чужих броней на нём — источник ответа availability. */
    private final Map<UUID, BigDecimal> stock = new HashMap<>();
    private final Map<UUID, BigDecimal> reservedByOthers = new HashMap<>();
    @Mock
    private ReservationService reservationService;
    @Mock
    private WarehouseService warehouseService;
    @Mock
    private WarehouseLookup warehouseLookup;
    @Mock
    private UserDirectory users;
    @Mock
    private SettingService settingService;
    @Mock
    private GiftRuleService giftRuleService;
    @Mock
    private GiftCertificateService giftCertificateService;
    @Mock
    private ReadyProductService readyProductService;

    /** Полка склада смены: сюда попадают возвраты и налитое по чеку. */
    private static final UUID SHELF = UUID.randomUUID();

    private PosShiftService shiftService;
    private SellUseCase sell;
    private SaleReturnUseCase saleReturn;
    private ToShelfUseCase toShelfUseCase;
    private VoidReceiptUseCase voidReceipt;
    private final SaleCommandMapper commands = new SaleCommandMapperImpl();

    @BeforeEach
    void setUp() {
        // сервис смен настоящий, над теми же моками: проверки доступа и статуса работают как в проде
        shiftService = new PosShiftServiceImpl(shiftRepository, docService,
                moneyService, events, users, warehouseLookup,
                new ShiftSummaryQueryServiceImpl(docService, users, giftCertificateService), entityManager, Clock.systemDefaultZone());
        // проводка документов смены тоже настоящая, чек проходит тот же путь, что в проде
        ShiftDocPoster shiftDocs =
                new ShiftDocPoster(docService, postingService, docEventService);
        // части чека тоже настоящие: склад, подарки, вода и возврат проверяются как в проде
        Clock clock = Clock.systemDefaultZone();
        BaseConsumption baseConsumption = new BaseConsumption(productService);
        sell = new SellUseCase(shiftService, productService, reservationService, giftCertificateService,
                shiftDocs, warehouseLookup, new SellerDiscountCap(settingService, productService),
                new ReceiptStockGuard(reservationService, productService), new ReceiptGiftGuard(giftRuleService),
                baseConsumption, clock);
        saleReturn = new SaleReturnUseCase(shiftService, docService, shiftDocs, warehouseLookup,
                readyProductService, clock);
        toShelfUseCase = new ToShelfUseCase(shiftService, productService, shiftDocs, warehouseLookup,
                readyProductService, baseConsumption, clock);
        voidReceipt = new VoidReceiptUseCase(shiftService, docService, postingService, docEventService,
                reservationService, users);
        lenient().when(warehouseLookup.shelfOf(WAREHOUSE)).thenReturn(SHELF);
        // по умолчанию у seller и admin есть доступ к тестовому складу
        allowWarehouse("seller", AppUser.Role.SELLER);
        allowWarehouse("admin", AppUser.Role.ADMIN);
        // доступность считает ReservationService: здесь её собирают из остатка склада смены
        // и резерва чужих броней (по умолчанию броней нет); на других складах пусто
        lenient().when(reservationService.availability(any(), any(), any())).thenAnswer(inv -> {
            boolean onShift = WAREHOUSE.equals(inv.getArgument(0));
            Map<UUID, Availability> out = new HashMap<>();
            for (UUID id : inv.<Collection<UUID>>getArgument(1)) {
                out.put(id, new Availability(
                        onShift ? stock.getOrDefault(id, BigDecimal.ZERO) : BigDecimal.ZERO,
                        onShift ? reservedByOthers.getOrDefault(id, BigDecimal.ZERO) : BigDecimal.ZERO));
            }
            return out;
        });
    }

    private void allowWarehouse(
            String login,
            AppUser.Role role
    ) {
        var u = new AppUser();
        u.setId("seller".equals(login) ? SELLER_ID : UUID.randomUUID());
        u.setLogin(login);
        u.setRole(role);
        u.getWarehouseIds().add(WAREHOUSE);
        lenient().when(users.byLogin(login)).thenReturn(Optional.of(u));
        lenient().when(users.require(login)).thenReturn(u);
        lenient().when(users.idByLogin(login)).thenReturn(u.getId());
        // имя в ShiftDto (openedBy/closedBy) ищется по id; displayName нет, поэтому имя совпадает с логином
        lenient().when(users.name(u.getId())).thenReturn(login);
    }

    private PosShift openShift(long openingCashMinor) {
        PosShift shift = new PosShift();
        shift.setId(UUID.randomUUID());
        shift.setNumber(7);
        shift.setWarehouseId(WAREHOUSE);
        shift.setStatus(PosShift.Status.OPEN);
        shift.setOpenedBy(SELLER_ID);
        shift.setOpeningCashMinor(openingCashMinor);
        when(shiftRepository.require(eq(shift.getId()), anyString())).thenReturn(shift);
        return shift;
    }

    /** Остатка на складе смены хватает, иначе продажа блокируется. */
    private void givenStock(
            UUID productId,
            String qty
    ) {
        stock.putAll(Map.of(productId, new BigDecimal(qty)));
    }

    /** Склад разлива: в наличии и масло, и парфюмерная вода. */
    private void givenOilAndWater(
            String oil,
            String water
    ) {
        stock.putAll(Map.of(
                id("test-oil-1"), new BigDecimal(oil), id("test-base-1"), new BigDecimal(water)));
    }

    /** Исходный чек продажи на qty штук, по нему оформляется возврат. */
    private StockDoc soldReceipt(
            UUID id,
            UUID productId,
            String qty
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(id);
        doc.setDocType(DocType.SALE);
        doc.setStatus(DocStatus.POSTED);
        doc.setShiftId(null);
        StockDocLine line = new StockDocLine();
        line.setProductId(productId);
        line.setQty(new BigDecimal(qty));
        // возврат сверяется со строкой чека по цене, скидке и подарку, поэтому чек должен быть правдоподобным
        line.setPriceMinor(100_000);
        doc.getLines().add(line);
        return doc;
    }

    @Test
    void createReceipt_rejectsPaymentMismatch() {
        PosShift shift = openShift(0);
        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("2"), 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null); // чек на 200_000, оплата 100_000

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("не сходится");
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_createsSaleLinksShiftAndPosts() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("2"), 100_000, BigDecimal.ZERO)),
                150_000L, 50_000L, "чек с кассы"), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        StockDoc sent = captor.getValue();
        assertThat(sent.getDocType()).isEqualTo(DocType.SALE);
        assertThat(sent.getWarehouseId()).isEqualTo(WAREHOUSE);
        assertThat(sent.getCashMinor()).isEqualTo(150_000);
        assertThat(sent.getNoCashMinor()).isEqualTo(50_000);
        assertThat(sent.getShiftId()).isEqualTo(shift.getId());
        // покупатель не закреплён, продажа без привязки
        assertThat(sent.getCounterpartyId()).isNull();
        verify(postingService).post(docId);
    }

    @Test
    void createReceipt_attachesCustomerToSale() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        UUID docId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("1"), 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, null, customerId), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        // закреплённый на кассе покупатель попадает в документ продажи, на этом держится история покупок
        assertThat(captor.getValue().getCounterpartyId()).isEqualTo(customerId);
    }

    @Test
    void createReturn_byReceiptLinksSourceAndPosts() {
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        StockDoc sale = soldReceipt(saleId, id("test-aroma-1"), "2");
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        when(docService.findPostedBySourceDoc(saleId)).thenReturn(List.of());

        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReturn(shift.getId(), new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, "возврат"), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        assertThat(captor.getValue().getDocType()).isEqualTo(DocType.RETURN);
        assertThat(captor.getValue().getShiftId()).isEqualTo(shift.getId());
        assertThat(captor.getValue().getSourceDocId()).isEqualTo(saleId);
        verify(postingService).post(docId);
    }

    /**
     * Продавать в минус нельзя: при остатке 1 два не продать. Отказываем до
     * проведения и с цифрами, чтобы кассир видел, чего не хватает.
     */
    @Test
    void createReceipt_rejectsNegativeStock() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "1"); // на остатке 1, продаём 2
        when(productService.nameOf(id("test-aroma-1"))).thenReturn("Aventus");

        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("2"), 100_000, BigDecimal.ZERO)),
                200_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("на складе 1, нужно 2")
                .hasMessageContaining("минус запрещена");
        verify(docService, never()).create(any());
    }

    /**
     * Оплата сертификатом: чек только запоминает бланк, а гасит его проведение,
     * чтобы распроведение могло его вернуть. Если гасить здесь, отмена чека
     * из админки оставит сертификат погашенным.
     */
    @Test
    void createReceipt_stampsCertificateOnDoc_andLeavesRedeemToPosting() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);
        GiftCertificate blank = new GiftCertificate();
        blank.setId(UUID.randomUUID());
        when(giftCertificateService.byCode("GC-001")).thenReturn(blank);

        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 180_000, BigDecimal.ZERO)),
                130_000L, 0L, null, null, null, null, "GC-001", 50_000L);
        createReceipt(shift.getId(), request, "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        assertThat(captor.getValue().getCertificateId()).isEqualTo(blank.getId());
        assertThat(captor.getValue().getCertificateMinor()).isEqualTo(50_000L);
        verify(postingService).post(docId);
    }

    /** Чужая бронь мешает продаже: это обещание другому покупателю, молча забирать её нельзя. */
    @Test
    void createReceipt_blocksWhenReservedByOthers() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "3");
        reservedByOthers.putAll(Map.of(id("test-aroma-1"), new BigDecimal("2")));

        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("2"), 100_000, BigDecimal.ZERO)),
                200_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("забронировано");
        verify(postingService, never()).post(any());
    }

    /** Активная непросроченная бронь склада смены с одной строкой. */
    private Reservation activeReservation(
            UUID productId,
            String qty
    ) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setWarehouseId(WAREHOUSE);
        reservation.setCustomerName("Тест Покупатель");
        reservation.setStatus(Reservation.Status.ACTIVE);
        reservation.setExpiresAt(LocalDateTime.now().plusDays(3));
        ReservationLine line = new ReservationLine();
        line.setId(UUID.randomUUID());
        line.setReservation(reservation);
        line.setProductId(productId);
        line.setQty(new BigDecimal(qty));
        line.setPriceMinor(100_000);
        line.setLineNo(1);
        reservation.getLines().add(line);
        return reservation;
    }

    @Test
    void createReceipt_blocksSaleIntoForeignReserve() {
        // остаток 1 целиком забронирован другим покупателем, продажа мимо брони отклоняется
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "1");
        reservedByOthers.putAll(Map.of(id("test-aroma-1"), BigDecimal.ONE));

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("забронировано");
        verify(postingService, never()).post(any());
    }

    @Test
    void createReceipt_fromReservationSellsOwnReserveAndMarksSold() {
        // остаток 1 целиком занят резервом этой же брони: свой резерв не мешает,
        // а после проведения бронь получает статус SOLD
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "1");
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);
        // чужих броней нет: резерв этой брони сервис не вычитает, потому что её id передан
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, reservation.getId()), "seller");

        verify(postingService).post(docId);
        verify(reservationService, atLeastOnce()).availability(eq(WAREHOUSE), any(), eq(reservation.getId()));
        verify(reservationService).markSold(reservation.getId(), docId);
    }

    /** Склад и каталог для чека по брони: test-aroma-1 в брони, test-aroma-2 — добавлен в чек. */
    private void givenReservationSaleStock(long catalogPriceOfReserved) {
        stock.putAll(Map.of(
                id("test-aroma-1"), BigDecimal.ONE, id("test-aroma-2"), BigDecimal.ONE));
        CatalogProduct reserved = new CatalogProduct();
        reserved.setId(id("test-aroma-1"));
        reserved.setPriceMinor(catalogPriceOfReserved);
        CatalogProduct extra = new CatalogProduct();
        extra.setId(id("test-aroma-2"));
        extra.setPriceMinor(100_000L);
        lenient().when(productService.findAllById(any())).thenReturn(List.of(reserved, extra));
        lenient().when(settingService.getInt(eq(SettingService.MAX_SELLER_DISCOUNT_PCT), ArgumentMatchers.anyInt()))
                .thenReturn(20);
    }

    @Test
    void createReceipt_fromReservationStillCapsDiscountOnOtherLines() {
        // бронь не открывает скидки на всё подряд: добавленный в чек товар по 100 с
        // при каталоге 1000 с продавцу не пробить
        PosShift shift = openShift(0);
        givenReservationSaleStock(100_000L);
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO),
                        new LineRequest(id("test-aroma-2"), BigDecimal.ONE, 10_000, BigDecimal.ZERO)),
                110_000L, 0L, null, reservation.getId()), "seller"))
                .isInstanceOf(AccessDeniedException.class);
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_fromReservationCapsDiscountBelowHeldPrice() {
        // строка брони продаётся по её цене, но не дешевле: 500 с при брони 1000 с — отказ
        PosShift shift = openShift(0);
        givenReservationSaleStock(100_000L);
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 50_000, BigDecimal.ZERO)),
                50_000L, 0L, null, reservation.getId()), "seller"))
                .isInstanceOf(AccessDeniedException.class);
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_fromReservationKeepsHeldPriceAfterCatalogRise() {
        // каталог подорожал до 1500 с, а бронь обещала 1000 с: продавец продаёт по брони,
        // хотя это ниже потолка от нового каталога
        PosShift shift = openShift(0);
        givenReservationSaleStock(150_000L);
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);
        UUID docId = givenPostedSale();

        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, reservation.getId()), "seller");

        verify(reservationService).markSold(reservation.getId(), docId);
    }

    @Test
    void createReceipt_fromReservationRejectsOtherCustomer() {
        PosShift shift = openShift(0);
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        reservation.setCounterpartyId(UUID.randomUUID());
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);
        givenStock(id("test-aroma-1"), "1");

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, reservation.getId(), UUID.randomUUID(), null, null, null), "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("покупателем брони");
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_fromReservationTakesCustomerFromReservation() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "1");
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        UUID customer = UUID.randomUUID();
        reservation.setCounterpartyId(customer);
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);
        givenPostedSale();

        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, reservation.getId()), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        assertThat(captor.getValue().getCounterpartyId()).isEqualTo(customer);
    }

    /** Документ продажи создаётся и проводится; возвращает его id. */
    private UUID givenPostedSale() {
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);
        return docId;
    }

    @Test
    void createReceipt_rejectsReservationOfOtherWarehouse() {
        // Бронь чужого склада к чеку этой смены не привязать.
        PosShift shift = openShift(0);
        Reservation reservation = activeReservation(id("test-aroma-1"), "1");
        reservation.setWarehouseId(UUID.randomUUID());
        when(reservationService.requireActiveForSale(reservation.getId())).thenReturn(reservation);

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null, reservation.getId()), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("другой склад");
        verify(postingService, never()).post(any());
        verify(reservationService, never()).markSold(any(), any());
    }

    @Test
    void createReturn_rejectsWithoutSourceReceipt() {
        PosShift shift = openShift(0);

        assertThatThrownBy(() -> createReturn(shift.getId(), new ReturnRequest(null,
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null), "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("по чеку");
    }

    @Test
    void createReturn_rejectsMoreThanSold() {
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        StockDoc sale = soldReceipt(saleId, id("test-aroma-1"), "2");
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        when(docService.findPostedBySourceDoc(saleId)).thenReturn(List.of());

        assertThatThrownBy(() -> createReturn(shift.getId(), new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-aroma-1"), new BigDecimal("3"), 100_000, BigDecimal.ZERO)),
                300_000L, 0L, null), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не больше");
        verify(postingService, never()).post(any());
    }

    @Test
    void createReturn_giftLineCannotBeReturnedForMoney() {
        // подарок продан за 0 (is_gift). Вернуть тот же товар как платный значит вынуть
        // деньги из кассы, и сверка строк должна это отклонить
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        StockDoc sale = new StockDoc();
        sale.setId(saleId);
        sale.setDocType(DocType.SALE);
        sale.setStatus(DocStatus.POSTED);
        sale.setShiftId(shift.getId());
        StockDocLine giftLine = new StockDocLine();
        giftLine.setProductId(id("test-gift-1"));
        giftLine.setQty(BigDecimal.ONE);
        giftLine.setPriceMinor(0);
        giftLine.setGift(true);
        sale.getLines().add(giftLine);
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        when(docService.findPostedBySourceDoc(saleId)).thenReturn(List.of());

        assertThatThrownBy(() -> createReturn(shift.getId(), new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-gift-1"), BigDecimal.ONE, 50_000, BigDecimal.ZERO)),
                50_000L, 0L, null), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не совпадает");
        verify(postingService, never()).post(any());
    }

    @Test
    void voidReceipt_rejectsForeignSeller() {
        PosShift shift = openShift(0);
        allowWarehouse("seller-2", AppUser.Role.SELLER);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        doc.setDocType(DocType.SALE);
        doc.setStatus(DocStatus.POSTED);
        doc.setShiftId(shift.getId());
        doc.setCreatedBy(UUID.randomUUID()); // чужой продавец (не seller-2)
        when(docService.findForUpdate(docId)).thenReturn(doc);

        assertThatThrownBy(() -> voidReceipt.handle(shift.getId(), docId, "seller-2"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("свой чек");
        verify(postingService, never()).unpost(any());
    }

    @Test
    void voidReceipt_rejectsSaleWithReturn() {
        PosShift shift = openShift(0);
        UUID docId = UUID.randomUUID();
        StockDoc sale = new StockDoc();
        sale.setId(docId);
        sale.setDocType(DocType.SALE);
        sale.setStatus(DocStatus.POSTED);
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(docId)).thenReturn(sale);
        // возврат по чеку уже был: сторно второй раз вернуло бы товар на склад и деньги покупателю
        StockDoc ret = new StockDoc();
        ret.setId(UUID.randomUUID());
        when(docService.findPostedBySourceDoc(docId)).thenReturn(List.of(ret));

        assertThatThrownBy(() -> voidReceipt.handle(shift.getId(), docId, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже оформлен возврат");
        verify(postingService, never()).unpost(docId);
    }

    @Test
    void voidReceipt_unpostsAndCancelsOwnShiftReceipt() {
        PosShift shift = openShift(0);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        doc.setDocType(DocType.SALE);
        doc.setStatus(DocStatus.POSTED);
        doc.setShiftId(shift.getId());
        when(docService.findForUpdate(docId)).thenReturn(doc);

        voidReceipt.handle(shift.getId(), docId, "seller");

        verify(postingService).unpost(docId);
        verify(docService).cancel(docId);
    }

    @Test
    void voidReceipt_rejectsForeignShiftReceipt() {
        PosShift shift = openShift(0);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        doc.setDocType(DocType.SALE);
        doc.setStatus(DocStatus.POSTED);
        doc.setShiftId(UUID.randomUUID()); // чужая смена

        when(docService.findForUpdate(docId)).thenReturn(doc);

        assertThatThrownBy(() -> voidReceipt.handle(shift.getId(), docId, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("другой смене");
        verify(postingService, never()).unpost(any());
    }

    @Test
    void createReceipt_rejectsSellerWithoutWarehouseAccess() {
        PosShift shift = openShift(0);
        var stranger = new AppUser();
        stranger.setId(UUID.randomUUID());
        stranger.setLogin("stranger");
        stranger.setRole(AppUser.Role.SELLER); // склад не назначен
        when(users.require("stranger")).thenReturn(stranger);

        assertThatThrownBy(() -> createReceipt(shift.getId(),
                new ReceiptRequest(List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 1000, null)),
                        1000L, 0L, null), "stranger"))
                .isInstanceOf(AccessDeniedException.class);
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_rejectsClosedShift() {
        PosShift shift = openShift(0);
        shift.setStatus(PosShift.Status.CLOSED);

        assertThatThrownBy(() -> createReceipt(shift.getId(),
                new ReceiptRequest(List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 1000, null)),
                        1000L, 0L, null), "seller"))
                .isInstanceOf(ConflictException.class);
    }

    private void givenCatalogPrice(
            UUID productId,
            long priceMinor
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(productId);
        p.setPriceMinor(priceMinor);
        lenient().when(productService.findAllById(any())).thenReturn(List.of(p));
        lenient().when(settingService.getInt(eq(SettingService.MAX_SELLER_DISCOUNT_PCT), ArgumentMatchers.anyInt()))
                .thenReturn(20);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReceipt_rejectsSellerDiscountBelowCap() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        givenCatalogPrice(id("test-aroma-1"), 100_000); // каталог 1000 с
        // продавец пробивает по 700 с: скидка 30% при потолке 20%
        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 70_000, BigDecimal.ZERO)),
                70_000L, 0L, null), "seller"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("администратор");
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_allowsSellerDiscountWithinCap() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        givenCatalogPrice(id("test-aroma-1"), 100_000);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        // 850 с: скидка 15%, в пределах потолка
        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 85_000, BigDecimal.ZERO)),
                85_000L, 0L, null), "seller");

        verify(postingService).post(docId);
    }

    @Test
    void createReceipt_adminBypassesDiscountCap() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        givenCatalogPrice(id("test-aroma-1"), 100_000);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        // админ опускает цену до 10 с, потолок на него не действует
        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 1_000, BigDecimal.ZERO)),
                1_000L, 0L, null), "admin");

        verify(postingService).post(docId);
    }

    private GiftRule sumRule(
            long minSumMinor,
            UUID giftProductId,
            String giftQty
    ) {
        GiftRule r = new GiftRule();
        r.setId(UUID.randomUUID());
        r.setName("акция");
        GiftRuleCondition c = new GiftRuleCondition();
        c.setType(GiftRuleCondition.Type.SUM);
        c.setGroupNo(0);
        c.setMinSumMinor(minSumMinor);
        r.getConditions().add(c);
        GiftItem it = new GiftItem();
        it.setProductId(giftProductId);
        it.setQty(new BigDecimal(giftQty));
        r.getItems().add(it);
        return r;
    }

    private void givenProducts(CatalogProduct... ps) {
        lenient().when(productService.findAllById(any())).thenReturn(List.of(ps));
    }

    private CatalogProduct product(
            UUID id,
            long priceMinor
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(id);
        p.setPriceMinor(priceMinor);
        return p;
    }

    @Test
    void createReceipt_rejectsGiftNotEarned() {
        PosShift shift = openShift(0);
        givenStock(id("gift-1"), "5");
        givenProducts(product(id("gift-1"), 30_000));
        // правило даёт подарок от 100 000 платной суммы, а в чеке только подарок
        when(giftRuleService.activeRules()).thenReturn(List.of(
                sumRule(100_000, id("gift-1"), "1")));
        UUID ruleId = giftRuleService.activeRules().getFirst().getId();

        assertThatThrownBy(() -> createReceipt(shift.getId(), new ReceiptRequest(
                List.of(new LineRequest(id("gift-1"), BigDecimal.ONE, 0, BigDecimal.ZERO,
                        true, ruleId, "g1")),
                0L, 0L, null, null, null, 0L, null, null), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("больше положенного");
        verify(docService, never()).create(any());
    }

    @Test
    void createReceipt_allowsEarnedGift() {
        PosShift shift = openShift(0);
        stock.putAll(Map.of(id("paid-1"), new BigDecimal("5"), id("gift-1"), new BigDecimal("5")));
        givenProducts(product(id("paid-1"), 100_000), product(id("gift-1"), 30_000));
        GiftRule rule = sumRule(100_000, id("gift-1"), "1");
        when(giftRuleService.activeRules()).thenReturn(List.of(rule));

        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        // платных строк на 100 000, это порог; подарок gift-1 из набора проходит
        createReceipt(shift.getId(), new ReceiptRequest(
                List.of(
                        new LineRequest(id("paid-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO),
                        new LineRequest(id("gift-1"), BigDecimal.ONE, 0, BigDecimal.ZERO,
                                true, rule.getId(), "g1")),
                100_000L, 0L, null, null, null, 100_000L, null, null), "seller");

        verify(postingService).post(docId);
    }

    /** Списание разлива тоже документ смены: отбор чеков и возвратов — запросом. */
    @Test
    void shiftReceipts_takesPostedReceiptsOfShift() {
        PosShift shift = openShift(0);
        StockDoc sale = soldReceipt(UUID.randomUUID(), id("test-aroma-1"), "1");
        when(docService.findPostedReceiptsOfShift(shift.getId())).thenReturn(List.of(sale));

        assertThat(shiftService.shiftReceipts(shift.getId(), "seller"))
                .containsExactly(sale);
    }

    /** Запрос проходит через тот же маппер, что в endpoint. */
    private StockDoc createReceipt(
            UUID shiftId,
            ReceiptRequest request,
            String login
    ) {
        return sell.handle(shiftId, commands.toSale(request), login);
    }

    private StockDoc createReturn(
            UUID shiftId,
            ReturnRequest request,
            String login
    ) {
        return saleReturn.handle(shiftId, commands.toReturn(request), login);
    }

    private StockDoc toShelf(
            UUID shiftId,
            PosShelfRequest request,
            String login
    ) {
        return toShelfUseCase.handle(shiftId, commands.toShelf(request), login);
    }

    /** Товар-основу сервер находит сам по системной форме BASE. */
    private CatalogProduct baseProduct() {
        CatalogProduct p = new CatalogProduct();
        p.setId(id("test-base-1"));
        p.setName("Парфюмерная вода");
        p.setKind(CatalogProduct.Kind.BASE.name());
        p.setUnit(CatalogProduct.Unit.ML);
        return p;
    }

    /** Захватывает строки, с которыми чек ушёл в документ. */
    private List<StockDocLine> capturedLines(
            PosShift shift,
            ReceiptRequest request
    ) {
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReceipt(shift.getId(), request, "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        return captor.getValue().getLines();
    }

    /** Строка разлива: qty — миллилитры масла, dilutionPct — доля масла в спрее. */
    private static LineRequest split(
            UUID id,
            String oilMl,
            long priceMinor,
            String pct
    ) {
        return new LineRequest(id, new BigDecimal(oilMl), priceMinor, BigDecimal.ZERO,
                null, null, null, null, new BigDecimal(pct), null, null);
    }

    @Test
    void receipt_addsWaterForDilutedSplit() {
        PosShift shift = openShift(0);
        givenOilAndWater("100", "100");
        when(productService.findActiveByKind(CatalogProduct.Kind.BASE.name()))
                .thenReturn(List.of(baseProduct()));
        // клиент просит 10 мл спрея пополам: 5 мл масла и 5 мл воды
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "5", 100_000, "50")), 500_000L, 0L, null);

        List<StockDocLine> lines = capturedLines(shift, request);

        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst().getProductId()).isEqualTo(id("test-oil-1"));
        // вода идёт по нулевой цене: клиент за неё не платит, а со склада она уходит
        StockDocLine water = lines.get(1);
        assertThat(water.getProductId()).isEqualTo(id("test-base-1"));
        assertThat(water.getQty()).isEqualByComparingTo("5");
        assertThat(water.getPriceMinor()).isZero();
        assertThat(water.getDilutionPct()).isNull();
    }

    @Test
    void receipt_rejectsWhenWaterRunsOut() {
        PosShift shift = openShift(0);
        // масла достаточно, а воды 3 мл, хотя флакону пополам из 5 мл масла нужно 5
        givenOilAndWater("100", "3");
        when(productService.findActiveByKind(CatalogProduct.Kind.BASE.name()))
                .thenReturn(List.of(baseProduct()));
        when(productService.nameOf(id("test-base-1"))).thenReturn("Парфюмерная вода");
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "5", 100_000, "50")), 500_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Парфюмерная вода")
                .hasMessageContaining("минус запрещена");
        verify(docService, never()).create(any());
    }

    @Test
    void receipt_waterFollowsConcentration() {
        PosShift shift = openShift(0);
        givenOilAndWater("100", "100");
        when(productService.findActiveByKind(CatalogProduct.Kind.BASE.name()))
                .thenReturn(List.of(baseProduct()));
        // 40% масла: на 6 мл масла 9 мл воды, всего 15 мл спрея
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "6", 100_000, "40")), 600_000L, 0L, null);

        List<StockDocLine> lines = capturedLines(shift, request);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).getQty()).isEqualByComparingTo("9");
    }

    @Test
    void receipt_pureOilHasNoWater() {
        PosShift shift = openShift(0);
        givenStock(id("test-oil-1"), "100");
        // концентрация 100 означает чистое масло, разбавлять нечем
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "5", 100_000, "100")), 500_000L, 0L, null);

        assertThat(capturedLines(shift, request)).hasSize(1);
        verify(productService, never()).findActiveByKind(CatalogProduct.Kind.BASE.name());
    }

    @Test
    void receipt_withoutBaseProductIsRejected() {
        PosShift shift = openShift(0);
        // разбавленного разлива без воды не бывает. Если пропустить такой чек, остаток воды
        // перестанет убывать, а себестоимость окажется заниженной
        when(productService.findActiveByKind(CatalogProduct.Kind.BASE.name())).thenReturn(List.of());
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "5", 100_000, "50")), 500_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("парфюмерная вода");
        verify(docService, never()).create(any());
    }

    @Test
    void receipt_dilutionOnBottleIsRejected() {
        PosShift shift = openShift(0);
        // концентрация у штучного товара — ошибка клиента. Молчать нельзя: магазин списал бы
        // воду на флакон и заметил бы это только по остатку
        CatalogProduct bottle = new CatalogProduct();
        bottle.setId(id("test-bottle-1"));
        bottle.setKind(CatalogProduct.Kind.BOTTLE.name());
        when(productService.findAllById(List.of(id("test-bottle-1")))).thenReturn(List.of(bottle));
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-bottle-1"), "1", 100_000, "50")), 100_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("только у разлива");
        verify(docService, never()).create(any());
    }

    /** Захватывает строки, с которыми возврат ушёл в документ. */
    private List<StockDocLine> capturedReturnLines(
            PosShift shift,
            ReturnRequest request
    ) {
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);

        createReturn(shift.getId(), request, "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        return captor.getValue().getLines();
    }

    @Test
    void return_putsGoodsOnShelf() {
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        StockDoc sale = soldReceipt(saleId, id("test-aroma-1"), "2");
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        // принесённое обратно идёт только на полку, не на обычный склад
        ReturnRequest request = new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null);

        List<StockDocLine> lines = capturedReturnLines(shift, request);

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().getProductId()).isEqualTo(id("test-aroma-1"));
        assertThat(lines.getFirst().getWarehouseId()).isEqualTo(SHELF);
    }

    /** Чек с налитым флаконом: масло, вода и атомайзер связаны одной меткой. */
    private StockDoc soldBottle(
            UUID id,
            UUID bundle
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(id);
        doc.setDocType(DocType.SALE);
        doc.setStatus(DocStatus.POSTED);
        String[][] parts = {{"test-oil-1", "5", "30000", "20000"}, {"test-base-1", "5", "0", "100"},
                {"test-atomizer-1", "1", "50000", "20000"}};
        for (String[] p : parts) {
            StockDocLine line = new StockDocLine();
            line.setId(UUID.randomUUID());
            line.setProductId(id(p[0]));
            line.setQty(new BigDecimal(p[1]));
            line.setPriceMinor(Long.parseLong(p[2]));
            line.setCostMinor(Long.parseLong(p[3]));
            line.setBundleId(bundle);
            doc.getLines().add(line);
        }
        return doc;
    }

    @Test
    void return_ofBundleBecomesReadyOnShelf() {
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        UUID bundle = UUID.randomUUID();
        StockDoc sale = soldBottle(saleId, bundle);
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        CatalogProduct ready = new CatalogProduct();
        ready.setId(id("test-ready-1"));
        ready.setKind(CatalogProduct.Kind.READY.name());
        when(readyProductService.findOrCreate(any())).thenReturn(ready);
        // кассир вернул только строку масла, но флакон возвращается целиком: масло, вода
        // и атомайзер одной готовой вещью, по цене, которую вернули клиенту
        ReturnRequest request = new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-oil-1"), new BigDecimal("5"), 30_000, BigDecimal.ZERO),
                        new LineRequest(id("test-atomizer-1"), BigDecimal.ONE, 50_000, BigDecimal.ZERO)),
                200_000L, 0L, null);

        List<StockDocLine> lines = capturedReturnLines(shift, request);

        assertThat(lines).hasSize(1);
        StockDocLine readyLine = lines.getFirst();
        assertThat(readyLine.getProductId()).isEqualTo(id("test-ready-1"));
        assertThat(readyLine.getQty()).isEqualByComparingTo("1");
        assertThat(readyLine.getPriceMinor()).isEqualTo(200_000);
        assertThat(readyLine.getWarehouseId()).isEqualTo(SHELF);
        assertThat(readyLine.getBundleId()).isEqualTo(bundle);
        // в составе готового все три строки связки, включая воду, которую клиент не видел
        ArgumentCaptor<List<ReadyProductService.Part>> parts = ArgumentCaptor.forClass(List.class);
        verify(readyProductService).findOrCreate(parts.capture());
        assertThat(parts.getValue()).extracting(ReadyProductService.Part::productId)
                .containsExactlyInAnyOrder(id("test-oil-1"), id("test-base-1"), id("test-atomizer-1"));
    }

    @Test
    void return_ofBundleTwiceIsRejected() {
        PosShift shift = openShift(0);
        UUID saleId = UUID.randomUUID();
        UUID bundle = UUID.randomUUID();
        StockDoc sale = soldBottle(saleId, bundle);
        sale.setShiftId(shift.getId());
        when(docService.findForUpdate(saleId)).thenReturn(sale);
        // Флакон уже вернули: в проведённом возврате есть строка с этой меткой.
        StockDoc earlier = new StockDoc();
        StockDocLine readyLine = new StockDocLine();
        readyLine.setProductId(id("test-ready-1"));
        readyLine.setQty(BigDecimal.ONE);
        readyLine.setPriceMinor(200_000);
        readyLine.setBundleId(bundle);
        earlier.getLines().add(readyLine);
        when(docService.findPostedBySourceDoc(saleId)).thenReturn(List.of(earlier));
        // касса передаёт метку флакона явно...
        ReturnRequest byBundle = new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-oil-1"), new BigDecimal("5"), 30_000, null, 0L, null,
                        null, null, null, bundle, null)),
                150_000L, 0L, null);
        assertThatThrownBy(() -> createReturn(shift.getId(), byBundle, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже возвращён");
        // ...но и старый клиент без метки не превратит строку из связки в возврат масла
        ReturnRequest byKey = new ReturnRequest(saleId,
                List.of(new LineRequest(id("test-oil-1"), new BigDecimal("5"), 30_000, BigDecimal.ZERO)),
                150_000L, 0L, null);
        assertThatThrownBy(() -> createReturn(shift.getId(), byKey, "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже возвращён");
        verify(docService, never()).create(any());
    }

    @Test
    void toShelf_assemblesReadyFromCartLines() {
        PosShift shift = openShift(0);
        when(productService.findActiveByKind(CatalogProduct.Kind.BASE.name()))
                .thenReturn(List.of(baseProduct()));
        CatalogProduct ready = new CatalogProduct();
        ready.setId(id("test-ready-1"));
        ready.setKind(CatalogProduct.Kind.READY.name());
        ready.setName("Aventus · Готовое · разлив 10 мл · 50 %");
        ready.setPriceMinor(200_000L);
        when(readyProductService.findOrCreate(any())).thenReturn(ready);
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);
        UUID bundle = UUID.randomUUID();
        // налили 10 мл пополам с атомайзером, клиент передумал, флакон идёт на полку
        PosShelfRequest request = new PosShelfRequest(List.of(
                new LineRequest(id("test-oil-1"), new BigDecimal("5"), 30_000, BigDecimal.ZERO,
                        null, null, null, null, new BigDecimal("50"), bundle, null),
                new LineRequest(id("test-atomizer-1"), BigDecimal.ONE, 50_000, BigDecimal.ZERO,
                        null, null, null, null, null, bundle, null)), null);

        toShelf(shift.getId(), request, "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        StockDoc created = captor.getValue();
        assertThat(created.getDocType()).isEqualTo(DocType.ASSEMBLY);
        assertThat(created.getWarehouseId()).isEqualTo(WAREHOUSE);
        assertThat(created.getWarehouseToId()).isEqualTo(SHELF);
        // масло, вода (её дописал сервер), атомайзер и сама готовая вещь
        assertThat(created.getLines()).extracting(StockDocLine::getProductId)
                .containsExactly(id("test-oil-1"), id("test-atomizer-1"), id("test-base-1"), id("test-ready-1"));
        assertThat(created.getLines().get(3).getQty()).isEqualByComparingTo("1");
        verify(postingService).post(docId);
    }

    @Test
    void receipt_rejectsDiscountOnKit() {
        PosShift shift = openShift(0);
        CatalogProduct kit = new CatalogProduct();
        kit.setId(id("test-kit-1"));
        kit.setName("Подарочный");
        kit.setKind(CatalogProduct.Kind.KIT.name());
        when(productService.findAllById(List.of(id("test-kit-1")))).thenReturn(List.of(kit));
        // цена комплекта фиксированная, скидки на него нет
        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-kit-1"), BigDecimal.ONE, 350_000, null, 50_000L,
                        null, null, null, null, null, null)),
                300_000L, 0L, null);

        assertThatThrownBy(() -> createReceipt(shift.getId(), request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("скидка не даётся");
        verify(docService, never()).create(any());
    }

    @Test
    void receipt_pureOilSellsWithoutBaseProduct() {
        PosShift shift = openShift(0);
        givenStock(id("test-oil-1"), "100");
        // неразбавленному маслу вода не нужна, и справочник тоже
        ReceiptRequest request = new ReceiptRequest(
                List.of(split(id("test-oil-1"), "5", 100_000, "100")), 500_000L, 0L, null);

        assertThat(capturedLines(shift, request)).hasSize(1);
    }

    @Test
    void receipt_ordinaryLinesUntouched() {
        PosShift shift = openShift(0);
        givenStock(id("test-aroma-1"), "5");
        // У обычной строки концентрации нет — воду дописывать не из чего.
        ReceiptRequest request = new ReceiptRequest(
                List.of(new LineRequest(id("test-aroma-1"), BigDecimal.ONE, 100_000, BigDecimal.ZERO)),
                100_000L, 0L, null);

        assertThat(capturedLines(shift, request)).hasSize(1);
    }
}
