package com.aromatica.modules.inventory.doc.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.event.DocCreatedEvent;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.QStockDoc;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.querydsl.core.BooleanBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocServiceImpl implements DocService {

    private static final List<DocType> SUPPLIER_DOCS = List.of(DocType.SUPPLY, DocType.RETURN_SUPPLY);

    StockDocRepository docRepository;
    WarehouseLookup warehouseLookup;
    ProductService productService;
    ApplicationEventPublisher events;
    EntityManager entityManager;
    Clock clock;

    @Transactional
    @Override
    public StockDoc create(StockDoc doc) {
        doc.setId(UUID.randomUUID());
        doc.setNumber(docRepository.nextNumber(doc.getDocType().name()));
        doc.setStatus(DocStatus.DRAFT);
        doc.setPostedAt(null);
        if (doc.getMoment() == null) {
            doc.setMoment(LocalDateTime.now(clock));
        }
        if (doc.getWarehouseId() == null) {
            doc.setWarehouseId(warehouseLookup.defaultWarehouseId());
        }
        List<StockDocLine> lines = new ArrayList<>(doc.getLines());
        doc.getLines().clear();
        addLines(doc, lines);
        StockDoc saved = docRepository.save(doc);
        events.publishEvent(new DocCreatedEvent(saved));
        return saved;
    }

    @Transactional
    @Override
    public StockDoc update(
            UUID id,
            StockDoc changes
    ) {
        StockDoc doc = find(id);
        doc.requireDraft("Редактировать");
        if (changes.getMoment() != null) {
            doc.setMoment(changes.getMoment());
        }
        if (changes.getWarehouseId() != null) {
            doc.setWarehouseId(changes.getWarehouseId());
        }
        doc.setWarehouseToId(changes.getWarehouseToId());
        doc.setCounterpartyId(changes.getCounterpartyId());
        doc.setComment(changes.getComment());
        doc.setCashMinor(changes.getCashMinor());
        doc.setNoCashMinor(changes.getNoCashMinor());
        doc.setPayAccountId(changes.getPayAccountId());
        if (!doc.getLines().isEmpty()) {
            doc.getLines().clear();
            // Hibernate вставляет новые строки раньше удаления старых — без flush падает unique (doc_id, line_no)
            entityManager.flush();
        }
        addLines(doc, changes.getLines());
        return doc;
    }

    @Transactional
    @Override
    public void cancel(UUID id) {
        find(id).cancel();
    }

    @Override
    public StockDoc find(UUID id) {
        return docRepository.require(id, "Документ не найден");
    }

    @Transactional
    @Override
    public StockDoc findForUpdate(UUID id) {
        StockDoc doc = find(id);
        // FOR UPDATE вернул бы уже прочитанный объект из сессии со старым состоянием — refresh
        // блокирует и перечитывает; flush до него, иначе refresh выбросит незаписанные правки
        entityManager.flush();
        entityManager.refresh(doc, LockModeType.PESSIMISTIC_WRITE);
        return doc;
    }

    @Override
    public List<StockDoc> findByShiftId(UUID shiftId) {
        return docRepository.findByShiftIdOrderByMomentDescNumberDesc(shiftId);
    }

    @Override
    public Map<UUID, Integer> lineCounts(Collection<UUID> ids) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        docRepository.countLines(ids).forEach(row -> counts.put(row.getDocId(), (int) row.getLineCount()));
        return counts;
    }

    @Override
    public List<StockDoc> findPostedReceiptsOfShift(UUID shiftId) {
        // в смене лежат не только чеки: списание разлива тоже её документ
        return docRepository.findByShiftIdAndStatusAndDocTypeInOrderByMomentDescNumberDesc(
                shiftId, DocStatus.POSTED, List.of(DocType.SALE, DocType.RETURN));
    }

    @Override
    public List<StockDoc> findPostedBySourceDoc(UUID sourceDocId) {
        return docRepository.findBySourceDocIdAndStatus(sourceDocId, DocStatus.POSTED);
    }

    @Override
    public List<StockDoc> findAllById(Collection<UUID> ids) {
        return docRepository.findAllById(ids);
    }

    @Override
    public List<StockDoc> lastSalesOf(
            UUID counterpartyId,
            int limit
    ) {
        return docRepository.findByCounterpartyIdAndDocTypeAndStatusOrderByMomentDescNumberDesc(
                counterpartyId, DocType.SALE, DocStatus.POSTED,
                PageRequest.of(0, limit));
    }

    @Override
    public List<SupplierDebt> supplierDebts() {
        return docRepository.sumUnpaidByCounterparty(SUPPLIER_DOCS, DocType.SUPPLY, DocStatus.POSTED).stream()
                .map(row -> new SupplierDebt(row.getCounterpartyId(), row.getDebtMinor()))
                .toList();
    }

    @Override
    public List<StockDoc> findUnpaidSupplierDocs(
            Collection<UUID> counterpartyIds,
            boolean withoutCounterparty
    ) {
        QStockDoc d = QStockDoc.stockDoc;
        BooleanBuilder whose = new BooleanBuilder();
        if (!counterpartyIds.isEmpty()) {
            whose.or(d.counterpartyId.in(counterpartyIds));
        }
        if (withoutCounterparty) {
            whose.or(d.counterpartyId.isNull());
        }
        if (!whose.hasValue()) {
            return List.of();
        }
        BooleanBuilder p = new BooleanBuilder(d.docType.in(SUPPLIER_DOCS))
                .and(d.status.eq(DocStatus.POSTED))
                .and(d.payAccountId.isNull())
                .and(whose);
        List<StockDoc> docs = new ArrayList<>();
        docRepository.findAll(p, d.moment.desc(), d.number.desc()).forEach(docs::add);
        return docs;
    }

    private void addLines(
            StockDoc doc,
            List<StockDocLine> lines
    ) {
        Set<UUID> splitIds = doc.getDocType() != DocType.SUPPLY ? Set.of()
                : productService.findAllById(lines.stream().map(StockDocLine::getProductId).distinct().toList())
                        .stream()
                        .filter(p -> p.hasKind(CatalogProduct.Kind.SPLIT))
                        .map(CatalogProduct::getId)
                        .collect(Collectors.toSet());
        int lineNo = 1;
        for (StockDocLine line : lines) {
            // у инвентаризации строка — фактический остаток, ноль допустим
            int minSignum = doc.getDocType() == DocType.INVENTORY ? 0 : 1;
            if (line.getQty().signum() < minSignum) {
                throw new BusinessRuleException("Строка " + lineNo + ": количество должно быть больше нуля");
            }
            if (splitIds.contains(line.getProductId())) {
                throw new BusinessRuleException("Строка " + lineNo
                        + ": распив нельзя принять — он получается вскрытием флакона/тестера");
            }
            // подарок в приёмке обнулил бы закупочную цену и испортил среднюю себестоимость
            if (line.isGift() && doc.getDocType() != DocType.SALE && doc.getDocType() != DocType.RETURN) {
                throw new BusinessRuleException("Строка " + lineNo
                        + ": подарочная строка допустима только в чеке продажи или возврата");
            }
            if (line.isGift()) {
                line.setPriceMinor(0);
                line.setDiscountMinor(0);
            }
            long gross = CostMath.grossMinor(line.getQty(), line.getPriceMinor());
            if (line.getDiscountMinor() > gross) {
                throw new BusinessRuleException("Строка " + lineNo
                        + ": скидка больше суммы строки");
            }
            line.setId(UUID.randomUUID());
            line.setDoc(doc);
            line.setDiscountPct(CostMath.discountPctFor(line.getQty(), line.getPriceMinor(), line.getDiscountMinor()));
            line.setLineNo(lineNo++);
            doc.getLines().add(line);
        }
    }
}
