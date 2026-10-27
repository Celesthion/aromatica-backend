package com.aromatica.modules.pos.shift.service;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.SellerLine;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.model.ShiftClosing;
import com.aromatica.modules.pos.shift.model.ShiftOpening;
import com.aromatica.modules.pos.shift.repository.PosShiftRepository;
import com.aromatica.modules.pos.shift.service.impl.PosShiftServiceImpl;
import com.aromatica.modules.pos.shift.service.impl.ShiftSummaryQueryServiceImpl;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Смены кассы: открытие, закрытие, сводка. Данные синтетические (test-aroma-N), не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class PosShiftServiceTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private EntityManager entityManager;
    @Mock
    private PosShiftRepository shiftRepository;
    @Mock
    private DocService docService;
    @Mock
    private MoneyService moneyService;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private WarehouseLookup warehouseLookup;
    @Mock
    private UserDirectory users;

    @Mock
    private GiftCertificateService giftCertificateService;

    private PosShiftService posShiftService;

    /** Сводка, как её строит endpoint: доступ к смене, затем подсчёт. */
    private ShiftSummary summaryOf(
            UUID shiftId,
            String login
    ) {
        return new ShiftSummaryQueryServiceImpl(docService, users, giftCertificateService)
                .of(posShiftService.requireAccessibleShift(shiftId, login));
    }

    @BeforeEach
    void setUp() {
        posShiftService = new PosShiftServiceImpl(shiftRepository, docService, moneyService,
                events, users, warehouseLookup, new ShiftSummaryQueryServiceImpl(docService, users, giftCertificateService), entityManager, Clock.systemDefaultZone());
        // по умолчанию у seller и admin есть доступ к тестовому складу
        allowWarehouse("seller", AppUser.Role.SELLER);
        allowWarehouse("admin", AppUser.Role.ADMIN);
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

    private static StockDoc postedDoc(
            DocType type,
            long sum,
            long cash,
            long noCash
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setDocType(type);
        doc.setStatus(DocStatus.POSTED);
        doc.setSumMinor(sum);
        doc.setCashMinor(cash);
        doc.setNoCashMinor(noCash);
        return doc;
    }

    @Test
    void openShift_rejectsSecondOpenOnSameWarehouse() {
        PosShift existing = new PosShift();
        existing.setNumber(3);
        when(shiftRepository.findFirstByWarehouseIdAndStatus(WAREHOUSE, PosShift.Status.OPEN))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> posShiftService.openShift(new ShiftOpening(WAREHOUSE, 0L), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже открыта");
    }

    @Test
    void openShift_rejectsSecondOpenBySameSeller() {
        PosShift own = new PosShift();
        own.setNumber(5);
        when(shiftRepository.findFirstByOpenedByAndStatus(SELLER_ID, PosShift.Status.OPEN))
                .thenReturn(Optional.of(own));

        assertThatThrownBy(() -> posShiftService.openShift(
                new ShiftOpening(UUID.randomUUID(), 0L), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже открыта смена");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void openShift_savesOpeningCashAndSeller() {
        when(shiftRepository.findFirstByWarehouseIdAndStatus(WAREHOUSE, PosShift.Status.OPEN))
                .thenReturn(Optional.empty());
        when(shiftRepository.nextNumber()).thenReturn(12L);
        when(shiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PosShift opened = posShiftService.openShift(new ShiftOpening(WAREHOUSE, 150_000L), "seller");

        assertThat(opened.getNumber()).isEqualTo(12);
        assertThat(opened.getStatus()).isEqualTo(PosShift.Status.OPEN);
        assertThat(opened.getOpeningCashMinor()).isEqualTo(150_000);
        assertThat(opened.getOpenedBy()).isEqualTo(users.idByLogin("seller"));
        assertThat(opened.getWarehouseId()).isEqualTo(WAREHOUSE);
    }

    @Test
    void summary_countsOnlyPostedReceiptsAndSubtractsCashReturns() {
        PosShift shift = openShift(100_000);
        StockDoc draft = postedDoc(DocType.SALE, 999, 999, 0);
        draft.setStatus(DocStatus.DRAFT);
        when(docService.findByShiftId(shift.getId())).thenReturn(List.of(
                postedDoc(DocType.SALE, 500_000, 200_000, 300_000),
                postedDoc(DocType.SALE, 300_000, 300_000, 0),
                postedDoc(DocType.RETURN, 100_000, 100_000, 0),
                draft));

        ShiftSummary s = summaryOf(shift.getId(), "admin");

        assertThat(s.receiptsCount()).isEqualTo(2);
        assertThat(s.revenueMinor()).isEqualTo(800_000);
        assertThat(s.cashMinor()).isEqualTo(400_000); // 500k нала − 100k возврат
        assertThat(s.noCashMinor()).isEqualTo(300_000);
        assertThat(s.returnsCount()).isEqualTo(1);
        assertThat(s.returnsMinor()).isEqualTo(100_000);
        // расчётный нал = размен + продажи наличными − возвраты наличными
        assertThat(s.expectedCashMinor()).isEqualTo(500_000);
    }

    @Test
    void closeShift_freezesExpectedCashAndStoresCounted() {
        PosShift shift = openShift(100_000);
        when(docService.findByShiftId(shift.getId()))
                .thenReturn(List.of(postedDoc(DocType.SALE, 250_000, 250_000, 0)));

        PosShift closed = posShiftService.closeShift(shift.getId(),
                new ShiftClosing(340_000L, "недостача"), "admin");

        assertThat(closed.getStatus()).isEqualTo(PosShift.Status.CLOSED);
        assertThat(closed.getExpectedCashMinor()).isEqualTo(350_000);
        assertThat(closed.getCountedCashMinor()).isEqualTo(340_000);
        assertThat(closed.getClosedBy()).isEqualTo(users.idByLogin("admin"));
        assertThat(shift.getClosedAt()).isNotNull();
    }

    @Test
    void closeShift_rejectsAlreadyClosed() {
        PosShift shift = openShift(0);
        shift.setStatus(PosShift.Status.CLOSED);

        assertThatThrownBy(() -> posShiftService.closeShift(shift.getId(),
                new ShiftClosing(0L, null), "admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("закрыта");
    }

    @Test
    void summary_subtractsReturnsFromAllPaymentBuckets() {
        PosShift shift = openShift(0);
        when(docService.findByShiftId(shift.getId())).thenReturn(List.of(
                postedDoc(DocType.SALE, 600_000, 200_000, 400_000),
                postedDoc(DocType.RETURN, 150_000, 50_000, 100_000)));

        ShiftSummary s = summaryOf(shift.getId(), "admin");

        assertThat(s.cashMinor()).isEqualTo(150_000);
        assertThat(s.noCashMinor()).isEqualTo(300_000);
        assertThat(s.expectedCashMinor()).isEqualTo(150_000);
    }

    @Test
    void closeShift_writesCashAdjustmentOnDiscrepancy() {
        PosShift shift = openShift(100_000);
        when(docService.findByShiftId(shift.getId()))
                .thenReturn(List.of(postedDoc(DocType.SALE, 250_000, 250_000, 0)));

        posShiftService.closeShift(shift.getId(), new ShiftClosing(300_000L, null), "admin");

        // ожидалось 350_000, насчитали 300_000: недостача 50_000 списывается со счёта наличных
        verify(moneyService).manualAdjustment(
                eq(MoneyAccount.Type.CASH), eq(-50_000L),
                ArgumentMatchers.contains("Недостача"));
    }

    @Test
    void closeShift_takesExclusiveLockBeforeCounting() {
        // эксклюзивная блокировка: закрытие дожидается чеков, начатых раньше, и считает итог с ними
        PosShift shift = openShift(0);
        when(docService.findByShiftId(shift.getId())).thenReturn(List.of());

        posShiftService.closeShift(shift.getId(), new ShiftClosing(0L, null), "admin");

        verify(entityManager).refresh(shift, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void lockOpenShift_rejectsShiftClosedMeanwhile() {
        // чек ждал закрытия на блокировке; после refresh смена уже закрыта — чек отклоняется
        PosShift shift = openShift(0);
        doAnswer(inv -> {
            shift.setStatus(PosShift.Status.CLOSED);
            return null;
        }).when(entityManager).refresh(shift, LockModeType.PESSIMISTIC_READ);

        assertThatThrownBy(() -> posShiftService.lockOpenShift(shift.getId(), "seller"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("закрыта");
    }

    @Test
    void summary_breaksDownRevenueBySeller() {
        PosShift shift = openShift(0);
        UUID aigerimId = UUID.randomUUID();
        UUID bakytId = UUID.randomUUID();
        when(users.names(any())).thenReturn(Map.of(aigerimId, "Айгерим", bakytId, "Бакыт"));
        when(users.logins(any())).thenReturn(Map.of(aigerimId, "aigerim", bakytId, "bakyt"));
        StockDoc a1 = postedDoc(DocType.SALE, 500_000, 500_000, 0);
        a1.setCreatedBy(aigerimId);
        StockDoc a2 = postedDoc(DocType.SALE, 200_000, 200_000, 0);
        a2.setCreatedBy(aigerimId);
        StockDoc b1 = postedDoc(DocType.SALE, 300_000, 300_000, 0);
        b1.setCreatedBy(bakytId);
        StockDoc bRet = postedDoc(DocType.RETURN, 100_000, 100_000, 0);
        bRet.setCreatedBy(bakytId);
        when(docService.findByShiftId(shift.getId())).thenReturn(List.of(a1, a2, b1, bRet));

        ShiftSummary s = summaryOf(shift.getId(), "admin");

        assertThat(s.perSeller()).hasSize(2);
        assertThat(s.perSeller().getFirst().name()).isEqualTo("Айгерим"); // больше выручка — первая
        assertThat(s.perSeller().getFirst().login()).isEqualTo("aigerim");
        assertThat(s.perSeller().getFirst().receiptsCount()).isEqualTo(2);
        assertThat(s.perSeller().getFirst().revenueMinor()).isEqualTo(700_000);
        assertThat(s.perSeller().get(1).name()).isEqualTo("Бакыт");
        assertThat(s.perSeller().get(1).login()).isEqualTo("bakyt");
        assertThat(s.perSeller().get(1).revenueMinor()).isEqualTo(300_000);
        assertThat(s.perSeller().get(1).returnsMinor()).isEqualTo(100_000);
    }

    @Test
    void summary_countsGiftsDiscountsWriteOffsAndCertificates() {
        PosShift shift = openShift(10_000);
        UUID anna = UUID.randomUUID();
        UUID boris = UUID.randomUUID();
        StockDoc cashSale = postedDoc(DocType.SALE, 9_000, 9_000, 0);
        cashSale.setCreatedBy(anna);
        cashSale.getLines().addAll(List.of(
                line(false, "1", 1_000, 500L),
                // подарок клиенту бесплатен, но склад теряет себестоимость
                line(true, "2", 0, 150L),
                line(true, "1", 0, null)));
        StockDoc cardSale = postedDoc(DocType.SALE, 2_000, 0, 2_000);
        cardSale.setCreatedBy(anna);
        StockDoc writeOff = postedDoc(DocType.WRITE_OFF, 0, 0, 0);
        writeOff.setCreatedBy(boris);
        writeOff.getLines().addAll(List.of(line(false, "3.5", 0, 100L), line(false, "1.5", 0, 200L)));
        when(docService.findByShiftId(shift.getId())).thenReturn(List.of(cashSale, cardSale, writeOff));
        when(giftCertificateService.soldInShift(shift.getId()))
                .thenReturn(List.of(certificate(5_000, 0), certificate(1_000, 4_000)));
        when(giftCertificateService.redeemedForDocs(List.of(cashSale.getId(), cardSale.getId()))).thenReturn(3_000L);

        ShiftSummary s = summaryOf(shift.getId(), "admin");

        assertThat(s.receiptsCount()).isEqualTo(2);
        assertThat(s.revenueMinor()).isEqualTo(11_000);
        assertThat(s.avgReceiptMinor()).isEqualTo(5_500);
        assertThat(s.discountsMinor()).isEqualTo(1_000);
        assertThat(s.giftsCount()).isEqualTo(2);
        assertThat(s.giftsCostMinor()).isEqualTo(300);
        assertThat(s.splitWriteOffMl()).isEqualByComparingTo("5");
        assertThat(s.splitWriteOffCostMinor()).isEqualTo(650);
        assertThat(s.certificatesSoldCount()).isEqualTo(2);
        assertThat(s.certificatesSoldMinor()).isEqualTo(10_000);
        assertThat(s.certificatesRedeemedMinor()).isEqualTo(3_000);
        // размен + продажи наличными + наличная часть сертификатов
        assertThat(s.expectedCashMinor()).isEqualTo(25_000);
        assertThat(s.perSeller()).extracting(SellerLine::writeOffMl, SellerLine::writeOffCostMinor)
                .containsExactly(tuple(BigDecimal.ZERO, 0L), tuple(new BigDecimal("5.0"), 650L));
    }

    private static StockDocLine line(
            boolean gift,
            String qty,
            long discountMinor,
            Long costMinor
    ) {
        StockDocLine line = new StockDocLine();
        line.setGift(gift);
        line.setQty(new BigDecimal(qty));
        line.setDiscountMinor(discountMinor);
        line.setCostMinor(costMinor);
        return line;
    }

    private static GiftCertificate certificate(
            long cashMinor,
            long noCashMinor
    ) {
        GiftCertificate c = new GiftCertificate();
        c.setSoldCashMinor(cashMinor);
        c.setSoldNoCashMinor(noCashMinor);
        return c;
    }
}
