package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.WriteOffReason;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosSplitWriteOffRequest;
import com.aromatica.modules.pos.sale.mapper.SaleCommandMapper;
import com.aromatica.modules.pos.sale.mapper.SaleCommandMapperImpl;
import com.aromatica.modules.pos.sale.service.ShiftDocPoster;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.repository.PosShiftRepository;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.modules.pos.shift.service.impl.PosShiftServiceImpl;
import com.aromatica.modules.pos.shift.service.impl.ShiftSummaryQueryServiceImpl;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Кнопка кассы «Не хватило» при наливе. Данные синтетические (test-split-N), не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class SplitWriteOffUseCaseTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock private PosShiftRepository shiftRepository;
    @Mock private EntityManager entityManager;
    @Mock private DocService docService;
    @Mock private PostingService postingService;
    @Mock private DocEventService docEventService;
    @Mock private MoneyService moneyService;
    @Mock private ApplicationEventPublisher events;
    @Mock private ProductService productService;
    @Mock private StockBalanceService stockBalanceService;
    @Mock private WarehouseService warehouseService;
    @Mock private WarehouseLookup warehouseLookup;
    @Mock private UserDirectory users;
    @Mock private GiftCertificateService giftCertificateService;

    private SplitWriteOffUseCase splitWriteOff;
    private final SaleCommandMapper commands = new SaleCommandMapperImpl();

    @BeforeEach
    void setUp() {
        // смены и проведение настоящие, над теми же моками: проверки доступа
        // и порядок проведения работают как в проде
        PosShiftService shiftService = new PosShiftServiceImpl(shiftRepository, docService,
                moneyService, events, users, warehouseLookup,
                new ShiftSummaryQueryServiceImpl(docService, users, giftCertificateService), entityManager, Clock.systemDefaultZone());
        splitWriteOff = new SplitWriteOffUseCase(shiftService, productService,
                stockBalanceService,
                new ShiftDocPoster(docService, postingService, docEventService), Clock.systemDefaultZone());
        allowWarehouse("seller", AppUser.Role.SELLER);
    }

    private void allowWarehouse(
            String login,
            AppUser.Role role
    ) {
        var u = new AppUser();
        u.setId(SELLER_ID);
        u.setLogin(login);
        u.setRole(role);
        u.getWarehouseIds().add(WAREHOUSE);
        lenient().when(users.byLogin(login)).thenReturn(Optional.of(u));
        lenient().when(users.require(login)).thenReturn(u);
        lenient().when(users.idByLogin(login)).thenReturn(u.getId());
        lenient().when(users.name(u.getId())).thenReturn(login);
    }

    private PosShift openShift() {
        PosShift shift = new PosShift();
        shift.setId(UUID.randomUUID());
        shift.setNumber(7);
        shift.setWarehouseId(WAREHOUSE);
        shift.setStatus(PosShift.Status.OPEN);
        shift.setOpenedBy(SELLER_ID);
        when(shiftRepository.require(eq(shift.getId()), anyString())).thenReturn(shift);
        return shift;
    }

    private void givenStock(
            UUID productId,
            String qty
    ) {
        when(stockBalanceService.qtyMap(eq(WAREHOUSE), any()))
                .thenReturn(Map.of(productId, new BigDecimal(qty)));
    }

    /** Распив: только такие позиции кассир правит кнопкой «Не хватило». */
    private CatalogProduct splitProduct(UUID id) {
        CatalogProduct p = new CatalogProduct();
        p.setId(id);
        p.setName("test-aroma-1 (распив)");
        p.setUnit(CatalogProduct.Unit.ML);
        return p;
    }

    private CatalogProduct pieceProduct(UUID id) {
        CatalogProduct p = new CatalogProduct();
        p.setId(id);
        p.setName("test-aroma-1 (флакон)");
        return p; // unit по умолчанию PCS
    }

    private StockDoc givenPostedDoc() {
        UUID docId = UUID.randomUUID();
        StockDoc doc = new StockDoc();
        doc.setId(docId);
        when(docService.create(any())).thenReturn(doc);
        StockDoc posted = new StockDoc();
        posted.setId(docId);
        when(postingService.post(docId)).thenReturn(posted);
        return doc;
    }

    @Test
    void writeOffSplit_postsWriteOffWithReasonAndShift() {
        PosShift shift = openShift();
        // по учёту 100 мл, из флакона вышло 80, фантомные 20 списываем
        givenStock(id("test-split-1"), "100");
        when(productService.find(id("test-split-1"))).thenReturn(splitProduct(id("test-split-1")));
        StockDoc doc = givenPostedDoc();

        splitWriteOff.handle(shift.getId(), commands.toWriteOff(new PosSplitWriteOffRequest(
                id("test-split-1"), new BigDecimal("20"), new BigDecimal("80"))), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        StockDoc created = captor.getValue();
        assertThat(created.getDocType()).isEqualTo(DocType.WRITE_OFF);
        assertThat(created.getWarehouseId()).isEqualTo(WAREHOUSE);
        assertThat(created.getLines()).singleElement().satisfies(l -> {
            assertThat(l.getProductId()).isEqualTo(id("test-split-1"));
            assertThat(l.getQty()).isEqualByComparingTo("20");
        });
        // сколько налили на самом деле, пишем в комментарий, чтобы разбор читался глазами
        assertThat(created.getComment()).contains("налито 80 мл");
        // смена нужна, чтобы попасть в сводку, причина — чтобы отличать от списаний из админки
        assertThat(created.getShiftId()).isEqualTo(shift.getId());
        assertThat(created.getWriteOffReason()).isEqualTo(WriteOffReason.BOTTLE_EMPTY);
        verify(postingService).post(doc.getId());
    }

    @Test
    void writeOffSplit_commentWithoutPouredMl() {
        PosShift shift = openShift();
        givenStock(id("test-split-1"), "100");
        when(productService.find(id("test-split-1"))).thenReturn(splitProduct(id("test-split-1")));
        givenPostedDoc();

        splitWriteOff.handle(shift.getId(), commands.toWriteOff(new PosSplitWriteOffRequest(
                id("test-split-1"), new BigDecimal("20"), null)), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        assertThat(captor.getValue().getComment()).doesNotContain("налито");
    }

    @Test
    void writeOffSplit_rejectsPieceGoods() {
        PosShift shift = openShift();
        // штучный товар этой кнопкой не правится, иначе она превратится
        // в универсальное «недостачи не было»
        when(productService.find(id("test-bottle-1"))).thenReturn(pieceProduct(id("test-bottle-1")));

        assertThatThrownBy(() -> splitWriteOff.handle(shift.getId(),
                commands.toWriteOff(new PosSplitWriteOffRequest(id("test-bottle-1"), BigDecimal.ONE, null)), "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("миллилитрах");
        verify(docService, never()).create(any());
    }

    @Test
    void writeOffSplit_rejectsMoreThanOnHand() {
        PosShift shift = openShift();
        givenStock(id("test-split-1"), "20");
        when(productService.find(id("test-split-1"))).thenReturn(splitProduct(id("test-split-1")));

        // списание не продажа: уводить остаток в минус нечему, это ошибка ввода
        assertThatThrownBy(() -> splitWriteOff.handle(shift.getId(),
                commands.toWriteOff(new PosSplitWriteOffRequest(id("test-split-1"), new BigDecimal("30"), null)), "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("больше остатка");
        verify(docService, never()).create(any());
    }

    @Test
    void writeOffSplit_allowsWholeRemainder() {
        PosShift shift = openShift();
        // «ничего не вышло»: флакон был пуст, списывается весь учётный остаток
        givenStock(id("test-split-1"), "30");
        when(productService.find(id("test-split-1"))).thenReturn(splitProduct(id("test-split-1")));
        StockDoc doc = givenPostedDoc();

        splitWriteOff.handle(shift.getId(), commands.toWriteOff(new PosSplitWriteOffRequest(
                id("test-split-1"), new BigDecimal("30"), BigDecimal.ZERO)), "seller");

        ArgumentCaptor<StockDoc> captor = ArgumentCaptor.forClass(StockDoc.class);
        verify(docService).create(captor.capture());
        assertThat(captor.getValue().getWriteOffReason()).isEqualTo(WriteOffReason.BOTTLE_EMPTY);
        verify(postingService).post(doc.getId());
    }
}
