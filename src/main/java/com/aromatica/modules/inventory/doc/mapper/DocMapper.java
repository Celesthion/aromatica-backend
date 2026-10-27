package com.aromatica.modules.inventory.doc.mapper;

import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocListItem;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocRequest;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.EventDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.LineDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.LineRequest;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** StockDoc → DTO, общий для админки и кассы: endpoint'ы друг друга не зовут. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocMapper {

    ProductService productService;
    CounterpartyService counterpartyService;
    UserDirectory users;

    public DocDto toDto(StockDoc doc) {
        return toDtos(List.of(doc)).getFirst();
    }

    /** Товары, контрагенты и люди — по запросу на весь список, а не на документ. */
    public List<DocDto> toDtos(List<StockDoc> docs) {
        Set<UUID> productIds = new HashSet<>();
        docs.forEach(d -> d.getLines().forEach(l -> productIds.add(l.getProductId())));
        Map<UUID, String> productNames = new HashMap<>();
        Map<UUID, String> productKinds = new HashMap<>();
        productService.findAllById(productIds).forEach(p -> {
            productNames.put(p.getId(), p.getName());
            productKinds.put(p.getId(), p.getKind());
        });
        Map<UUID, String> counterpartyNames = counterpartyService.names(
                docs.stream().map(StockDoc::getCounterpartyId).toList());
        Map<UUID, String> authors = users.names(docs.stream().map(StockDoc::getCreatedBy).toList());
        return docs.stream()
                .map(d -> toDto(d, productNames, productKinds, counterpartyNames, authors))
                .toList();
    }

    private static DocDto toDto(
            StockDoc doc,
            Map<UUID, String> productNames,
            Map<UUID, String> productKinds,
            Map<UUID, String> counterpartyNames,
            Map<UUID, String> authors
    ) {
        List<LineDto> lines = doc.getLines().stream()
                .map(l -> new LineDto(l.getId(), l.getProductId(),
                        productNames.getOrDefault(l.getProductId(), l.getProductId().toString()),
                        productKinds.get(l.getProductId()),
                        l.getQty(), l.getPriceMinor(), l.getDiscountPct(), l.getDiscountMinor(), l.getCostMinor(),
                        l.sumMinor(),
                        l.getLineNo(), l.isGift(), l.getDilutionPct(), l.getBundleId(), l.getWarehouseId()))
                .toList();

        return new DocDto(doc.getId(), doc.getDocType(), doc.getNumber(), doc.getStatus(),
                doc.getMoment(), doc.getWarehouseId(), doc.getWarehouseToId(),
                doc.getCounterpartyId(), counterpartyNames.get(doc.getCounterpartyId()),
                doc.currentSumMinor(), doc.getComment(),
                doc.getCashMinor(), doc.getNoCashMinor(), doc.getPayAccountId(),
                doc.getCreatedAt(), doc.getPostedAt(),
                authors.get(doc.getCreatedBy()), lines);
    }

    public List<DocListItem> toListItems(
            List<StockDoc> docs,
            Map<UUID, Integer> lineCounts
    ) {
        Map<UUID, String> counterpartyNames = counterpartyService.names(
                docs.stream().map(StockDoc::getCounterpartyId).toList());
        return docs.stream()
                .map(d -> new DocListItem(d.getId(), d.getDocType(), d.getNumber(), d.getStatus(),
                        d.getMoment(), counterpartyNames.get(d.getCounterpartyId()),
                        d.currentSumMinor(), lineCounts.getOrDefault(d.getId(), 0)))
                .toList();
    }

    /** Скидку старые клиенты присылают процентом — здесь она становится суммой. */
    public StockDoc toEntity(DocRequest request) {
        List<StockDocLine> lines = request.lines() == null ? List.of()
                : request.lines().stream().map(DocMapper::toLine).toList();
        StockDoc doc = StockDoc.draft(request.type(), request.moment(), request.warehouseId(),
                request.comment(), lines);
        doc.setWarehouseToId(request.warehouseToId());
        doc.setCounterpartyId(request.counterpartyId());
        doc.setCashMinor(request.cashMinor());
        doc.setNoCashMinor(request.noCashMinor());
        doc.setPayAccountId(request.payAccountId());
        return doc;
    }

    private static StockDocLine toLine(LineRequest request) {
        StockDocLine line = StockDocLine.of(request.productId(), request.qty(), request.priceMinor(),
                request.resolvedDiscountMinor());
        line.setGift(request.gift());
        line.setDilutionPct(request.dilutionPct());
        line.setBundleId(request.bundleId());
        line.setWarehouseId(request.warehouseId());
        return line;
    }

    public List<EventDto> toEventDtos(List<DocEvent> events) {
        Map<UUID, String> names = users.names(events.stream().map(DocEvent::getActorId).toList());
        return events.stream()
                .map(e -> new EventDto(e.getAction().name(), names.get(e.getActorId()), e.getAt()))
                .toList();
    }
}
