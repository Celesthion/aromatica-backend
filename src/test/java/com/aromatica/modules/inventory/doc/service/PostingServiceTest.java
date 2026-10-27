package com.aromatica.modules.inventory.doc.service;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.posting.AssemblyPosting;
import com.aromatica.modules.inventory.doc.posting.ConvertPosting;
import com.aromatica.modules.inventory.doc.posting.DisassemblyPosting;
import com.aromatica.modules.inventory.doc.posting.DocPostings;
import com.aromatica.modules.inventory.doc.posting.InventoryPosting;
import com.aromatica.modules.inventory.doc.posting.MovePosting;
import com.aromatica.modules.inventory.doc.posting.ReturnPosting;
import com.aromatica.modules.inventory.doc.posting.ReturnSupplyPosting;
import com.aromatica.modules.inventory.doc.posting.SalePosting;
import com.aromatica.modules.inventory.doc.posting.SupplyPosting;
import com.aromatica.modules.inventory.doc.posting.SurplusPosting;
import com.aromatica.modules.inventory.doc.posting.WriteOffPosting;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import com.aromatica.modules.inventory.doc.service.impl.PostingServiceImpl;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import com.aromatica.modules.inventory.stock.repository.StockBalanceRepository;
import com.aromatica.modules.inventory.stock.repository.StockMovementRepository.BalanceFold;
import com.aromatica.modules.inventory.stock.repository.StockMovementRepository;
import com.aromatica.modules.inventory.stock.service.impl.StockLedgerImpl;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Правила проведения. Товары и цены синтетические (test-aroma-N), не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    private static final UUID PRODUCT = id("test-aroma-1");
    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Mock
    private StockDocRepository docRepository;
    @Mock
    private DocService docService;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private StockBalanceRepository balanceRepository;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private ProductService productService;
    @Mock
    private WarehouseLookup warehouseLookup;
    /** Через него баланс перечитывается под блокировкой, в юнит-тесте это заглушка. */
    @Mock
    private EntityManager entityManager;

    private PostingService postingService;

    private StockBalance balance;

    /** Тот же набор стратегий, что поднимет Spring. */
    static DocPostings allPostings(StockDocRepository docRepository) {
        return new DocPostings(List.of(new SupplyPosting(), new SalePosting(), new WriteOffPosting(),
                new SurplusPosting(), new InventoryPosting(), new MovePosting(), new ConvertPosting(),
                new ReturnPosting(docRepository), new ReturnSupplyPosting(), new AssemblyPosting(),
                new DisassemblyPosting()));
    }

    @BeforeEach
    void setUp() {
        // журнал остатков настоящий, над теми же моками репозиториев: блокировки и движения как в проде
        postingService = new PostingServiceImpl(docService,
                new StockLedgerImpl(balanceRepository, movementRepository, warehouseLookup, entityManager), events,
                productService, warehouseLookup, allPostings(docRepository), Clock.systemDefaultZone());
        balance = new StockBalance(PRODUCT, WAREHOUSE);
        // в базе свёртка журнала всегда даёт строку; пустой журнал — ноль по нулю
        lenient().when(movementRepository.foldBalance(any(), any())).thenReturn(fold("0", 0));
        // id движений выдаёт последовательность пачкой, по порядку
        AtomicLong sequence = new AtomicLong();
        lenient().when(movementRepository.nextIds(anyInt())).thenAnswer(inv -> LongStream
                .rangeClosed(1, inv.<Integer>getArgument(0)).map(i -> sequence.incrementAndGet()).boxed().toList());
    }

    private static BalanceFold fold(
            String qty,
            long avgCostMinor
    ) {
        return new BalanceFold() {
            @Override public BigDecimal getQty() { return new BigDecimal(qty); }
            @Override public long getAvgCostMinor() { return avgCostMinor; }
        };
    }

    private void givenBalance(
            String qty,
            long avgMinor
    ) {
        balance.setQty(new BigDecimal(qty));
        balance.setAvgCostMinor(avgMinor);
        when(balanceRepository.findForUpdate(any(), any())).thenReturn(Optional.of(balance));
    }

    private StockDoc draft(
            DocType type,
            StockDocLine... lines
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setDocType(type);
        doc.setNumber(1);
        doc.setStatus(DocStatus.DRAFT);
        doc.setMoment(LocalDateTime.of(2026, 1, 15, 12, 0));
        doc.setWarehouseId(WAREHOUSE);
        doc.setLines(new ArrayList<>(List.of(lines)));
        when(docService.findForUpdate(doc.getId())).thenReturn(doc);
        return doc;
    }

    private static StockDocLine line(
            String qty,
            long priceMinor
    ) {
        StockDocLine l = new StockDocLine();
        l.setId(UUID.randomUUID());
        l.setProductId(PRODUCT);
        l.setQty(new BigDecimal(qty));
        l.setPriceMinor(priceMinor);
        l.setDiscountPct(BigDecimal.ZERO);
        l.setLineNo(1);
        return l;
    }

    @Test
    void supply_intoEmptyStock_setsQtyAndAvg() {
        givenBalance("0", 0);
        StockDoc doc = draft(DocType.SUPPLY, line("100", 50_00));

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("100");
        assertThat(balance.getAvgCostMinor()).isEqualTo(50_00);
        assertThat(doc.getStatus()).isEqualTo(DocStatus.POSTED);
        assertThat(doc.getSumMinor()).isEqualTo(100 * 50_00L);
        assertThat(doc.getPostedAt()).isNotNull();

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(entityManager).persist(captor.capture());
        StockMovement m = captor.getValue();
        assertThat(m.getQtyDelta()).isEqualByComparingTo("100");
        assertThat(m.getCostMinor()).isEqualTo(50_00);
        assertThat(m.getMoment()).isEqualTo(doc.getMoment());
    }

    @Test
    void supply_ontoExistingStock_recalculatesWeightedAvg() {
        givenBalance("10", 100_00);
        StockDoc doc = draft(DocType.SUPPLY, line("10", 200_00));

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("20");
        assertThat(balance.getAvgCostMinor()).isEqualTo(150_00);
    }

    @Test
    void sale_writesCostFromAvg() {
        givenBalance("8", 80_00);
        StockDocLine saleLine = line("5", 150_00);
        StockDoc doc = draft(DocType.SALE, saleLine);

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("3");
        assertThat(balance.getAvgCostMinor()).isEqualTo(80_00); // средняя при расходе не меняется
        assertThat(saleLine.getCostMinor()).isEqualTo(80_00);   // себестоимость зафиксирована в строке
        assertThat(doc.getSumMinor()).isEqualTo(5 * 150_00L);   // итог продажи — по продажной цене

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getQtyDelta()).isEqualByComparingTo("-5");
        assertThat(captor.getValue().getCostMinor()).isEqualTo(80_00);
    }

    @Test
    void sale_blocksNegativeStock() {
        // продавать в минус нельзя и на уровне проведения: документ из админки
        // тоже не должен увести остаток ниже нуля
        givenBalance("2", 80_00);
        StockDoc doc = draft(DocType.SALE, line("5", 150_00));

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Недостаточно остатка для продажи");
        assertThat(balance.getQty()).isEqualByComparingTo("2");
    }

    @Test
    void writeOff_sumIsByCost_notByPrice() {
        givenBalance("10", 60_00);
        StockDoc doc = draft(DocType.WRITE_OFF, line("4", 0));

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("6");
        assertThat(doc.getSumMinor()).isEqualTo(4 * 60_00L);
    }

    @Test
    void writeOff_blocksMoreThanStock() {
        givenBalance("3", 60_00);
        StockDoc doc = draft(DocType.WRITE_OFF, line("5", 0));

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Недостаточно остатка");
        assertThat(balance.getQty()).isEqualByComparingTo("3");
    }

    @Test
    void surplus_usesLinePrice_orAvgWhenNoPrice() {
        givenBalance("10", 100_00);
        StockDoc doc = draft(DocType.SURPLUS, line("10", 200_00));

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("20");
        assertThat(balance.getAvgCostMinor()).isEqualTo(150_00);
    }

    @Test
    void inventory_generatesShortageDiff() {
        givenBalance("10", 50_00);
        StockDoc doc = draft(DocType.INVENTORY, line("8", 0)); // факт 8 при учётных 10

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("8");
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getQtyDelta()).isEqualByComparingTo("-2");
        assertThat(captor.getValue().getCostMinor()).isEqualTo(50_00);
        assertThat(doc.getSumMinor()).isEqualTo(2 * 50_00L); // сумма разницы по себестоимости
    }

    @Test
    void post_rejectsNonDraft_andEmptyDocs() {
        StockDoc posted = draft(DocType.SUPPLY, line("1", 100));
        posted.setStatus(DocStatus.POSTED);
        assertThatThrownBy(() -> postingService.post(posted.getId()))
                .isInstanceOf(ConflictException.class);

        StockDoc empty = draft(DocType.SUPPLY);
        assertThatThrownBy(() -> postingService.post(empty.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unpost_deletesMovements_andRebuildsBalanceFromJournal() {
        StockDocLine soldLine = line("5", 150_00);
        soldLine.setCostMinor(80_00L);
        StockDoc doc = draft(DocType.SALE, soldLine);
        doc.setStatus(DocStatus.POSTED);
        doc.setPostedAt(LocalDateTime.now());

        StockMovement saleMovement = new StockMovement();
        saleMovement.setProductId(PRODUCT);
        saleMovement.setWarehouseId(WAREHOUSE);
        saleMovement.setQtyDelta(new BigDecimal("-5"));
        when(movementRepository.findByDocId(doc.getId())).thenReturn(List.of(saleMovement));

        // после удаления движений документа свёртка журнала даёт приёмку 20 шт по 80.00
        when(movementRepository.foldBalance(PRODUCT, WAREHOUSE)).thenReturn(fold("20", 80_00));

        balance.setQty(new BigDecimal("15"));
        balance.setAvgCostMinor(80_00);
        when(balanceRepository.findForUpdate(PRODUCT, WAREHOUSE)).thenReturn(Optional.of(balance));

        postingService.unpost(doc.getId());

        verify(movementRepository).deleteByDocId(doc.getId());
        assertThat(balance.getQty()).isEqualByComparingTo("20");
        assertThat(balance.getAvgCostMinor()).isEqualTo(80_00);
        assertThat(doc.getStatus()).isEqualTo(DocStatus.DRAFT);
        assertThat(doc.getPostedAt()).isNull();
        assertThat(soldLine.getCostMinor()).isNull(); // себестоимость строки снята
    }

    @Test
    void unpost_locksBalancesInSameOrderAsPost() {
        // движения пришли в «случайном» порядке, а блокировки должны идти по ключу (товар, склад),
        // как при проведении, иначе параллельные проведение и распроведение упрутся в дедлок
        StockDoc doc = draft(DocType.SALE, line("1", 100_00));
        doc.setStatus(DocStatus.POSTED);
        // first — меньший ключ; движения приходят в обратном порядке
        UUID first = id("test-a").compareTo(id("test-b")) < 0 ? id("test-a") : id("test-b");
        UUID second = first.equals(id("test-a")) ? id("test-b") : id("test-a");
        StockMovement b = new StockMovement();
        b.setProductId(second);
        b.setWarehouseId(WAREHOUSE);
        StockMovement a = new StockMovement();
        a.setProductId(first);
        a.setWarehouseId(WAREHOUSE);
        when(movementRepository.findByDocId(doc.getId())).thenReturn(List.of(b, a));
        when(balanceRepository.findForUpdate(any(), eq(WAREHOUSE)))
                .thenAnswer(inv -> Optional.of(new StockBalance(inv.getArgument(0), WAREHOUSE)));

        postingService.unpost(doc.getId());

        InOrder order = inOrder(balanceRepository);
        order.verify(balanceRepository).findForUpdate(first, WAREHOUSE);
        order.verify(balanceRepository).findForUpdate(second, WAREHOUSE);
    }

    @Test
    void unpostReturn_keepsPresetCost_soRepostUsesIt() {
        // готовое, собранное из связки, строки в чеке не имеет: его себестоимость 120.00
        // посчитана заранее и лежит в строке возврата. Распроведение её не стирает,
        // иначе повторное проведение взяло бы нулевую среднюю полки
        StockDocLine ret = line("1", 300_00);
        ret.setCostMinor(120_00L);
        StockDoc doc = draft(DocType.RETURN, ret);
        doc.setStatus(DocStatus.POSTED);
        when(movementRepository.findByDocId(doc.getId())).thenReturn(List.of());

        postingService.unpost(doc.getId());

        assertThat(doc.getStatus()).isEqualTo(DocStatus.DRAFT);
        assertThat(ret.getCostMinor()).isEqualTo(120_00);

        when(balanceRepository.findForUpdate(PRODUCT, WAREHOUSE)).thenReturn(Optional.of(balance));
        postingService.post(doc.getId());

        assertThat(balance.getAvgCostMinor()).isEqualTo(120_00);
    }

    @Test
    void convert_movesCostFromBottleToSplitMl() {
        // вскрываем 1 флакон себестоимостью 5000.00, получаем 100 мл распива по 50.00/мл
        StockBalance bottleBalance = new StockBalance(id("test-bottle"), WAREHOUSE);
        bottleBalance.setQty(new BigDecimal("3"));
        bottleBalance.setAvgCostMinor(5000_00);
        StockBalance splitBalance = new StockBalance(id("test-split"), WAREHOUSE);
        when(balanceRepository.findForUpdate(id("test-bottle"), WAREHOUSE)).thenReturn(Optional.of(bottleBalance));
        when(balanceRepository.findForUpdate(id("test-split"), WAREHOUSE)).thenReturn(Optional.of(splitBalance));

        StockDocLine source = line("1", 0);
        source.setProductId(id("test-bottle"));
        StockDocLine target = line("100", 0);
        target.setProductId(id("test-split"));
        StockDoc doc = draft(DocType.CONVERT, source, target);

        postingService.post(doc.getId());

        assertThat(bottleBalance.getQty()).isEqualByComparingTo("2");
        assertThat(splitBalance.getQty()).isEqualByComparingTo("100");
        assertThat(splitBalance.getAvgCostMinor()).isEqualTo(50_00);
        assertThat(source.getCostMinor()).isEqualTo(5000_00);
        assertThat(target.getCostMinor()).isEqualTo(50_00);
        assertThat(doc.getSumMinor()).isEqualTo(5000_00); // перенесённая себестоимость
        assertThat(doc.getStatus()).isEqualTo(DocStatus.POSTED);
    }

    @Test
    void convert_requiresExactlyTwoDifferentLines() {
        StockDoc oneLine = draft(DocType.CONVERT, line("1", 0));
        assertThatThrownBy(() -> postingService.post(oneLine.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void move_transfersQtyAndCostBetweenWarehouses() {
        UUID warehouseTo = UUID.randomUUID();
        StockBalance from = new StockBalance(PRODUCT, WAREHOUSE);
        from.setQty(new BigDecimal("10"));
        from.setAvgCostMinor(70_00);
        StockBalance to = new StockBalance(PRODUCT, warehouseTo);
        when(balanceRepository.findForUpdate(PRODUCT, WAREHOUSE)).thenReturn(Optional.of(from));
        when(balanceRepository.findForUpdate(PRODUCT, warehouseTo)).thenReturn(Optional.of(to));

        StockDocLine moveLine = line("4", 0);
        StockDoc doc = draft(DocType.MOVE, moveLine);
        doc.setWarehouseToId(warehouseTo);

        postingService.post(doc.getId());

        assertThat(from.getQty()).isEqualByComparingTo("6");
        assertThat(to.getQty()).isEqualByComparingTo("4");
        assertThat(to.getAvgCostMinor()).isEqualTo(70_00);   // себестоимость приехала с товаром
        assertThat(from.getAvgCostMinor()).isEqualTo(70_00); // у источника не изменилась
        assertThat(moveLine.getCostMinor()).isEqualTo(70_00);
        assertThat(doc.getSumMinor()).isEqualTo(4 * 70_00L);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(entityManager, Mockito.times(2)).persist(captor.capture());
        List<StockMovement> movements = captor.getAllValues();
        assertThat(movements.getFirst().getWarehouseId()).isEqualTo(WAREHOUSE);
        assertThat(movements.getFirst().getQtyDelta()).isEqualByComparingTo("-4");
        assertThat(movements.get(1).getWarehouseId()).isEqualTo(warehouseTo);
        assertThat(movements.get(1).getQtyDelta()).isEqualByComparingTo("4");
    }

    @Test
    void move_requiresDifferentTargetWarehouse() {
        StockDoc noTarget = draft(DocType.MOVE, line("1", 0));
        assertThatThrownBy(() -> postingService.post(noTarget.getId()))
                .isInstanceOf(ConflictException.class);

        StockDoc sameTarget = draft(DocType.MOVE, line("1", 0));
        sameTarget.setWarehouseToId(WAREHOUSE);
        assertThatThrownBy(() -> postingService.post(sameTarget.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void return_addsStockAtAvg_sumIsRefundByPrice() {
        givenBalance("10", 80_00);
        StockDocLine returnLine = line("2", 150_00); // покупатель вернул 2 шт, продавались по 150
        StockDoc doc = draft(DocType.RETURN, returnLine);

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("12");
        assertThat(balance.getAvgCostMinor()).isEqualTo(80_00); // приход по средней её не меняет
        assertThat(returnLine.getCostMinor()).isEqualTo(80_00);
        assertThat(doc.getSumMinor()).isEqualTo(2 * 150_00L);   // столько вернём денег
        verify(events).publishEvent(new DocPostedEvent(doc)); // деньги слушают это событие
    }

    @Test
    void returnSupply_removesStockAtAvg() {
        givenBalance("10", 80_00);
        StockDoc doc = draft(DocType.RETURN_SUPPLY, line("3", 0));

        postingService.post(doc.getId());

        assertThat(balance.getQty()).isEqualByComparingTo("7");
        assertThat(doc.getSumMinor()).isEqualTo(3 * 80_00L); // цена не задана → по себестоимости
    }

    /**
     * Проведение никого не зовёт напрямую, а публикует событие, на которое
     * подписаны деньги, сертификаты и лента. Распроведение устроено так же.
     */
    @Test
    void post_publishesPostedEvent_andUnpostPublishesUnposted() {
        givenBalance("0", 0);
        StockDoc doc = draft(DocType.SUPPLY, line("1", 100));

        postingService.post(doc.getId());
        verify(events).publishEvent(new DocPostedEvent(doc));

        when(movementRepository.findByDocId(doc.getId())).thenReturn(List.of());
        postingService.unpost(doc.getId());
        verify(events).publishEvent(new DocUnpostedEvent(doc));
    }

    /** Событие уходит с уже проведённым документом: слушателям нужны статус и итог. */
    @Test
    void postedEvent_carriesPostedDoc() {
        givenBalance("10", 80_00);
        StockDoc doc = draft(DocType.SALE, line("1", 300_00));

        postingService.post(doc.getId());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        StockDoc seen = ((DocPostedEvent) published.getValue()).doc();
        assertThat(seen.getStatus()).isEqualTo(DocStatus.POSTED);
        assertThat(seen.getSumMinor()).isEqualTo(300_00L);
    }

    /** Без разбивки чек оплачен наличными целиком — до события, чтобы деньги провели уже итог. */
    @Test
    void sale_withoutPaymentSplit_isPaidInCash() {
        givenBalance("10", 80_00);
        StockDoc doc = draft(DocType.SALE, line("2", 300_00));

        postingService.post(doc.getId());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(((DocPostedEvent) published.getValue()).doc().getCashMinor()).isEqualTo(600_00L);
    }

    @Test
    void sale_paymentSplitMustMatchTotal() {
        givenBalance("10", 80_00);
        StockDoc doc = draft(DocType.SALE, line("2", 300_00));
        doc.setCashMinor(100_00);
        doc.setNoCashMinor(400_00);

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не сходится");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unpost_rejectsDraft() {
        StockDoc doc = draft(DocType.SALE, line("1", 100));
        assertThatThrownBy(() -> postingService.unpost(doc.getId()))
                .isInstanceOf(ConflictException.class);
    }

    private static final UUID SHELF = UUID.randomUUID();

    private static CatalogProduct product(
            UUID id,
            CatalogProduct.Kind kind
    ) {
        CatalogProduct p = new CatalogProduct();
        p.setId(id);
        p.setName(id.toString());
        p.setKind(kind.name());
        p.setUnit(CatalogProduct.Unit.PCS);
        return p;
    }

    private static ProductComponent component(
            UUID id,
            String qty
    ) {
        ProductComponent c = new ProductComponent();
        c.setComponentId(id);
        c.setQty(new BigDecimal(qty));
        return c;
    }

    private static StockDocLine lineOf(
            UUID productId,
            String qty,
            long priceMinor
    ) {
        StockDocLine l = line(qty, priceMinor);
        l.setProductId(productId);
        return l;
    }

    /** Свой баланс на каждую пару товар-склад: комплект затрагивает несколько. */
    private Map<UUID, StockBalance> givenBalances(
            UUID warehouse,
            Map<UUID, String[]> byProduct
    ) {
        Map<UUID, StockBalance> balances = new HashMap<>();
        byProduct.forEach((productId, qtyAndAvg) -> {
            StockBalance b = new StockBalance(productId, warehouse);
            b.setQty(new BigDecimal(qtyAndAvg[0]));
            b.setAvgCostMinor(Long.parseLong(qtyAndAvg[1]));
            balances.put(productId, b);
            when(balanceRepository.findForUpdate(eq(productId), eq(warehouse))).thenReturn(Optional.of(b));
        });
        return balances;
    }

    @Test
    void kitSale_onStore_writesOffComponents() {
        // комплект на складе магазина виртуальный: продаются его составляющие,
        // себестоимость строки равна их сумме
        CatalogProduct kit = product(id("test-kit-1"), CatalogProduct.Kind.KIT);
        when(productService.findAllById(any())).thenReturn(List.of(kit));
        when(productService.componentsOf(List.of(id("test-kit-1")))).thenReturn(Map.of(id("test-kit-1"),
                List.of(component(id("test-box-1"), "1"), component(id("test-bottle-1"), "2"))));
        Map<UUID, StockBalance> balances = givenBalances(WAREHOUSE, Map.of(
                id("test-box-1"), new String[] {"10", "5000"},
                id("test-bottle-1"), new String[] {"10", "100000"}));
        StockDocLine kitLine = lineOf(id("test-kit-1"), "1", 350_000);
        StockDoc doc = draft(DocType.SALE, kitLine);

        postingService.post(doc.getId());

        assertThat(balances.get(id("test-box-1")).getQty()).isEqualByComparingTo("9");
        assertThat(balances.get(id("test-bottle-1")).getQty()).isEqualByComparingTo("8");
        assertThat(kitLine.getCostMinor()).isEqualTo(5_000 + 2 * 100_000);
        assertThat(doc.getSumMinor()).isEqualTo(350_000);
        // баланс самого комплекта на складе магазина не появляется
        verify(balanceRepository, Mockito.never()).findForUpdate(eq(id("test-kit-1")), any());
    }

    @Test
    void kitSale_onStore_blocksWhenComponentShort() {
        CatalogProduct kit = product(id("test-kit-1"), CatalogProduct.Kind.KIT);
        when(productService.findAllById(any())).thenReturn(List.of(kit));
        when(productService.componentsOf(List.of(id("test-kit-1")))).thenReturn(Map.of(id("test-kit-1"),
                List.of(component(id("test-box-1"), "1"), component(id("test-bottle-1"), "2"))));
        givenBalances(WAREHOUSE, Map.of(
                id("test-box-1"), new String[] {"10", "5000"},
                id("test-bottle-1"), new String[] {"1", "100000"}));
        StockDoc doc = draft(DocType.SALE, lineOf(id("test-kit-1"), "1", 350_000));

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("продажи комплекта");
    }

    @Test
    void compositeOutsideShelf_isRejected() {
        // готовое и комплект физически лежат только на полке. Приёмка комплекта
        // на склад магазина — ошибка, приход идёт по составляющим
        CatalogProduct kit = product(id("test-kit-1"), CatalogProduct.Kind.KIT);
        when(productService.findAllById(any())).thenReturn(List.of(kit));
        StockDoc doc = draft(DocType.SUPPLY, lineOf(id("test-kit-1"), "3", 100_000));

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("только на полке");
    }

    @Test
    void assembly_movesComponentsIntoReadyOnShelf() {
        // сборка: масло, вода и атомайзер уходят со склада смены, готовая вещь
        // приходуется на полку с суммой их себестоимостей
        CatalogProduct ready = product(id("test-ready-1"), CatalogProduct.Kind.READY);
        CatalogProduct oil = product(id("test-oil-1"), CatalogProduct.Kind.BULK);
        CatalogProduct water = product(id("test-base-1"), CatalogProduct.Kind.BASE);
        CatalogProduct atomizer = product(id("test-atomizer-1"), CatalogProduct.Kind.ATOMIZER);
        when(productService.findAllById(any())).thenReturn(List.of(ready, oil, water, atomizer));
        when(productService.componentsOf(List.of(id("test-ready-1")))).thenReturn(Map.of(id("test-ready-1"),
                List.of(component(id("test-oil-1"), "5"), component(id("test-base-1"), "5"),
                        component(id("test-atomizer-1"), "1"))));
        lenient().when(warehouseLookup.isShelf(any())).thenAnswer(inv -> SHELF.equals(inv.getArgument(0)));
        Map<UUID, StockBalance> store = givenBalances(WAREHOUSE, Map.of(
                id("test-oil-1"), new String[] {"100", "20000"},
                id("test-base-1"), new String[] {"1000", "100"},
                id("test-atomizer-1"), new String[] {"10", "20000"}));
        Map<UUID, StockBalance> shelf = givenBalances(SHELF, Map.of(
                id("test-ready-1"), new String[] {"0", "0"}));
        StockDocLine readyLine = lineOf(id("test-ready-1"), "1", 0);
        StockDoc doc = draft(DocType.ASSEMBLY, lineOf(id("test-oil-1"), "5", 0),
                lineOf(id("test-base-1"), "5", 0), lineOf(id("test-atomizer-1"), "1", 0), readyLine);
        doc.setWarehouseToId(SHELF);

        postingService.post(doc.getId());

        assertThat(store.get(id("test-oil-1")).getQty()).isEqualByComparingTo("95");
        assertThat(store.get(id("test-base-1")).getQty()).isEqualByComparingTo("995");
        assertThat(store.get(id("test-atomizer-1")).getQty()).isEqualByComparingTo("9");
        assertThat(shelf.get(id("test-ready-1")).getQty()).isEqualByComparingTo("1");
        long expectedCost = 5 * 20_000 + 5 * 100 + 20_000;
        assertThat(shelf.get(id("test-ready-1")).getAvgCostMinor()).isEqualTo(expectedCost);
        assertThat(readyLine.getCostMinor()).isEqualTo(expectedCost);
        assertThat(doc.getSumMinor()).isEqualTo(expectedCost);
    }

    @Test
    void assembly_rejectsLinesThatDontMatchComposition() {
        CatalogProduct ready = product(id("test-ready-1"), CatalogProduct.Kind.READY);
        CatalogProduct oil = product(id("test-oil-1"), CatalogProduct.Kind.BULK);
        when(productService.findAllById(any())).thenReturn(List.of(ready, oil));
        when(productService.componentsOf(List.of(id("test-ready-1")))).thenReturn(Map.of(id("test-ready-1"),
                List.of(component(id("test-oil-1"), "5"), component(id("test-atomizer-1"), "1"))));
        // балансы блокируются до проверки состава, поэтому нужны заглушки
        givenBalances(WAREHOUSE, Map.of(id("test-oil-1"), new String[] {"100", "20000"},
                id("test-ready-1"), new String[] {"0", "0"}));
        // в документе нет атомайзера: внутри вещи оказалось бы не то, что написано
        StockDoc doc = draft(DocType.ASSEMBLY, lineOf(id("test-oil-1"), "5", 0), lineOf(id("test-ready-1"), "1", 0));

        assertThatThrownBy(() -> postingService.post(doc.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не сходятся с составом");
    }

    @Test
    void return_usesCostOfSourceReceiptLine() {
        // на полке товара не было (средняя 0), возврат приходуется по себестоимости чека
        UUID saleId = UUID.randomUUID();
        StockDoc sale = new StockDoc();
        sale.setId(saleId);
        StockDocLine sold = lineOf(PRODUCT, "1", 150_00);
        sold.setCostMinor(80_00L);
        sale.getLines().add(sold);
        when(docRepository.findById(saleId)).thenReturn(Optional.of(sale));
        lenient().when(warehouseLookup.isShelf(any())).thenAnswer(inv -> SHELF.equals(inv.getArgument(0)));
        Map<UUID, StockBalance> shelf = givenBalances(SHELF, Map.of(PRODUCT, new String[] {"0", "0"}));
        StockDocLine ret = lineOf(PRODUCT, "1", 150_00);
        ret.setWarehouseId(SHELF);
        StockDoc doc = draft(DocType.RETURN, ret);
        doc.setSourceDocId(saleId);

        postingService.post(doc.getId());

        assertThat(shelf.get(PRODUCT).getQty()).isEqualByComparingTo("1");
        assertThat(shelf.get(PRODUCT).getAvgCostMinor()).isEqualTo(80_00);
        assertThat(ret.getCostMinor()).isEqualTo(80_00);
        assertThat(doc.getSumMinor()).isEqualTo(150_00);
    }
}
