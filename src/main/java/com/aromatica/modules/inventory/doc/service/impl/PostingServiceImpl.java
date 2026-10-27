package com.aromatica.modules.inventory.doc.service.impl;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.posting.DocPosting;
import com.aromatica.modules.inventory.doc.posting.DocPostings;
import com.aromatica.modules.inventory.doc.posting.PostingContext;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.inventory.stock.service.StockLedger;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Общий порядок проведения; правила типа — в стратегиях {@link DocPosting}. Порядок: проверки →
 * блокировки балансов → стратегия → движения пачкой → сверка оплаты → событие.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostingServiceImpl implements PostingService {

    DocService docService;
    StockLedger ledger;
    /** Деньги, сертификаты и лента подписаны на события и работают в этой же транзакции. */
    ApplicationEventPublisher events;
    ProductService productService;
    WarehouseLookup warehouseLookup;
    DocPostings postings;
    Clock clock;

    @Transactional
    @Override
    public StockDoc post(UUID docId) {
        // второй «Провести» (двойной клик) дождётся блокировки и увидит не черновик — без задвоения
        StockDoc doc = docService.findForUpdate(docId);
        doc.requireDraft("Провести");
        if (doc.getLines().isEmpty()) {
            throw new ConflictException("Документ без строк провести нельзя");
        }

        DocPosting posting = postings.of(doc.getDocType());
        PostingContext ctx = context(doc);
        // дешёвые проверки — до блокировок
        posting.validate(doc, ctx);
        requireCompositesOnShelf(doc, posting, ctx);
        // все блокировки заранее и в общем порядке: иначе MOVE A→B и B→A возьмут их накрест (дедлок)
        Set<BalanceKey> keys = new TreeSet<>();
        posting.collectBalanceKeys(doc, ctx, keys);
        // стратегия берёт балансы из контекста, а не блокирует заново
        ctx.balances().putAll(ledger.lock(keys));

        long docSum = posting.post(doc, ctx);

        ledger.record(ctx.movements());
        doc.setSumMinor(docSum);
        // разбивку оплаты деньги проводят как есть, поэтому её правят до события о проведении
        posting.settle(doc);
        doc.markPosted(LocalDateTime.now(clock));
        // исключение слушателя (деньги, сертификат) откатит проведение целиком
        events.publishEvent(new DocPostedEvent(doc));
        return doc;
    }

    private PostingContext context(StockDoc doc) {
        Set<UUID> ids = new HashSet<>();
        doc.getLines().forEach(l -> ids.add(l.getProductId()));
        Map<UUID, CatalogProduct> products = new HashMap<>();
        productService.findAllById(ids).forEach(p -> products.put(p.getId(), p));
        List<UUID> compositeIds = products.values().stream()
                .filter(CatalogProduct::isComposite).map(CatalogProduct::getId).toList();
        Map<UUID, List<ProductComponent>> compositions = productService.componentsOf(compositeIds);

        Set<UUID> warehouses = new HashSet<>();
        warehouses.add(doc.getWarehouseId());
        if (doc.getWarehouseToId() != null) {
            warehouses.add(doc.getWarehouseToId());
        }
        doc.getLines().forEach(l -> {
            if (l.getWarehouseId() != null) {
                warehouses.add(l.getWarehouseId());
            }
        });
        Set<UUID> shelves = new HashSet<>();
        warehouses.forEach(w -> {
            if (warehouseLookup.isShelf(w)) {
                shelves.add(w);
            }
        });
        return new PostingContext(products, compositions, shelves, new HashMap<>(), new ArrayList<>());
    }

    /** Вещь с составом живёт на полке; на складе магазина — только где разрешает стратегия. */
    private static void requireCompositesOnShelf(
            StockDoc doc,
            DocPosting posting,
            PostingContext ctx
    ) {
        for (StockDocLine line : doc.getLines()) {
            if (ctx.isComposite(line.getProductId())
                    && !ctx.isShelf(PostingContext.lineWarehouse(doc, line))
                    && !posting.allowsCompositeOffShelf(line, ctx)) {
                throw new ConflictException("«" + ctx.productName(line.getProductId())
                        + "»: комплект и готовое лежат только на полке");
            }
        }
    }

    @Transactional
    @Override
    public StockDoc unpost(UUID docId) {
        StockDoc doc = docService.findForUpdate(docId);
        doc.requirePosted("Распровести");

        ledger.revert(doc.getId());

        // себестоимость строки — итог проведения, её снимаем; у возврата она входные данные
        if (!postings.of(doc.getDocType()).keepsLineCostsOnUnpost()) {
            doc.getLines().forEach(line -> line.setCostMinor(null));
        }
        doc.markUnposted();
        events.publishEvent(new DocUnpostedEvent(doc));
        return doc;
    }
}
