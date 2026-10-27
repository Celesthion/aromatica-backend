package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ReadyProductService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import com.aromatica.modules.pos.sale.model.SaleReturn;
import com.aromatica.modules.pos.sale.service.ReceiptLines;
import com.aromatica.modules.pos.sale.service.ShiftDocPoster;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Возврат строго по чеку своей смены: по каждой строке не больше, чем продано минус уже возвращено.
 * Флакон из связки (масло, вода, атомайзер) возвращается целиком и ложится на полку одной готовой вещью.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SaleReturnUseCase {

    PosShiftService shiftService;
    DocService docService;
    /** Общая последовательность документов смены — см. ShiftDocPoster. */
    ShiftDocPoster shiftDocs;
    WarehouseLookup warehouseLookup;
    /** Готовое: во что превращается связка, когда ложится на полку. */
    ReadyProductService readyProductService;
    Clock clock;

    @Transactional
    public StockDoc handle(
            UUID shiftId,
            SaleReturn command,
            String login
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, login);
        if (command.sourceReceiptId() == null) {
            throw new BusinessRuleException("Возврат возможен только по чеку продажи");
        }
        // под блокировкой: иначе два параллельных возврата оба увидят «вернуть ещё можно»
        // и деньги уйдут покупателю дважды
        StockDoc source = docService.findForUpdate(command.sourceReceiptId());
        if (source.getDocType() != DocType.SALE || !source.isPosted()
                || !shift.getId().equals(source.getShiftId())) {
            throw new ConflictException("Возврат возможен только по проведённому чеку этой смены");
        }
        UUID shelf = warehouseLookup.shelfOf(shift.getWarehouseId());
        // строка из связки тянет за собой всю связку: флакон возвращается целиком
        Set<UUID> returnedBundles = returnedBundles(source);
        Set<UUID> bundles = requestedBundles(source, command.lines(), returnedBundles);
        List<ReceiptLine> lines = new ArrayList<>();
        command.lines().stream().filter(l -> !inBundle(source, l, bundles)).forEach(lines::add);
        source.getLines().stream()
                .filter(l -> l.getBundleId() != null && bundles.contains(l.getBundleId()))
                .map(l -> new ReceiptLine(l.getProductId(), l.getQty(), l.getPriceMinor(),
                        l.getDiscountMinor(), l.isGift(), null, null, l.getDilutionPct(),
                        l.getBundleId(), null))
                .forEach(lines::add);
        requireReturnable(source, lines);
        if (bundles.stream().anyMatch(returnedBundles::contains)) {
            throw new ConflictException("Этот флакон по чеку уже возвращён");
        }
        ReceiptLines.requirePayment(lines, command.cashMinor(), command.noCashMinor());

        Map<UUID, Long> bundleCosts = new HashMap<>();
        List<ReceiptLine> shelved = toShelfLines(source, lines, shelf, bundleCosts);
        // возврат наследует покупателя исходной продажи: деньги возвращаем ему же
        StockDoc doc = StockDoc.draft(DocType.RETURN, LocalDateTime.now(clock), shift.getWarehouseId(),
                command.comment(), ReceiptLines.toDocLines(shelved));
        doc.setCounterpartyId(source.getCounterpartyId());
        doc.setCashMinor(command.cashMinor());
        doc.setNoCashMinor(command.noCashMinor());
        doc.setSourceDocId(source.getId());
        doc.getLines().forEach(l -> {
            if (l.getBundleId() != null && bundleCosts.containsKey(l.getBundleId())) {
                l.setCostMinor(bundleCosts.get(l.getBundleId()));
            }
        });
        return shiftDocs.post(doc, shift, login);
    }

    /**
     * Метки связок из запроса. Строку без метки (старый клиент) относим к первой ещё не возвращённой
     * связке с такой строкой: два одинаковых флакона в чеке иначе не различить.
     */
    private static Set<UUID> requestedBundles(
            StockDoc source,
            List<ReceiptLine> requested,
            Set<UUID> alreadyReturned
    ) {
        Set<UUID> bundles = new LinkedHashSet<>();
        for (ReceiptLine r : requested) {
            if (r.bundleId() != null) {
                if (source.getLines().stream().noneMatch(l -> r.bundleId().equals(l.getBundleId()))) {
                    throw new ConflictException("Такого флакона в чеке нет");
                }
                bundles.add(r.bundleId());
            }
        }
        for (ReceiptLine r : requested) {
            if (r.bundleId() != null) {
                continue;
            }
            String key = lineKey(r.productId(), r.qty(), r.priceMinor(), r.discountMinor(),
                    r.gift());
            List<UUID> candidates = source.getLines().stream()
                    .filter(l -> l.getBundleId() != null)
                    .filter(l -> lineKey(l.getProductId(), l.getQty(), l.getPriceMinor(),
                            l.getDiscountMinor(), l.isGift()).equals(key))
                    .map(StockDocLine::getBundleId)
                    .distinct()
                    .toList();
            if (candidates.isEmpty() || candidates.stream().anyMatch(bundles::contains)) {
                continue; // строка не из связки либо её связка уже выбрана
            }
            UUID free = candidates.stream().filter(c -> !alreadyReturned.contains(c)).findFirst()
                    // строка связки не может стать обычным возвратом масла на полку
                    .orElseThrow(() -> new ConflictException("Этот флакон по чеку уже возвращён"));
            bundles.add(free);
        }
        return bundles;
    }

    /** Строка запроса — часть одной из выбранных связок (по метке или по ключу строки). */
    private static boolean inBundle(
            StockDoc source,
            ReceiptLine r,
            Set<UUID> bundles
    ) {
        if (r.bundleId() != null) {
            return bundles.contains(r.bundleId());
        }
        String key = lineKey(r.productId(), r.qty(), r.priceMinor(), r.discountMinor(),
                r.gift());
        return source.getLines().stream().anyMatch(l -> l.getBundleId() != null
                && bundles.contains(l.getBundleId())
                && lineKey(l.getProductId(), l.getQty(), l.getPriceMinor(), l.getDiscountMinor(), l.isGift())
                        .equals(key));
    }

    /** Связки чека, уже вернувшиеся на полку: у их готового в возврате та же метка. */
    private Set<UUID> returnedBundles(StockDoc source) {
        Set<UUID> returned = new HashSet<>();
        for (StockDoc ret : docService.findPostedBySourceDoc(source.getId())) {
            for (StockDocLine l : ret.getLines()) {
                if (l.getBundleId() != null) {
                    returned.add(l.getBundleId());
                }
            }
        }
        return returned;
    }

    /**
     * Связка ложится одной строкой готового: цена — сумма её строк (вернули клиенту),
     * себестоимость — сумма себестоимостей строк чека.
     */
    private List<ReceiptLine> toShelfLines(
            StockDoc source,
            List<ReceiptLine> lines,
            UUID shelf,
            Map<UUID, Long> bundleCosts
    ) {
        List<ReceiptLine> result = new ArrayList<>();
        Map<UUID, List<ReceiptLine>> byBundle = new LinkedHashMap<>();
        for (ReceiptLine l : lines) {
            if (l.bundleId() != null) {
                byBundle.computeIfAbsent(l.bundleId(), k -> new ArrayList<>()).add(l);
            } else {
                result.add(l.onWarehouse(shelf));
            }
        }
        for (var e : byBundle.entrySet()) {
            List<ReceiptLine> parts = e.getValue();
            CatalogProduct ready = readyProductService.findOrCreate(parts.stream()
                    .map(l -> new ReadyProductService.Part(l.productId(), l.qty())).toList());
            long price = parts.stream()
                    .mapToLong(l -> CostMath.lineSumMinor(l.qty(), l.priceMinor(), l.discountMinor()))
                    .sum();
            long cost = 0;
            for (StockDocLine sl : source.getLines()) {
                if (e.getKey().equals(sl.getBundleId())) {
                    cost += sl.costSumMinor();
                }
            }
            bundleCosts.put(e.getKey(), cost);
            result.add(new ReceiptLine(ready.getId(), BigDecimal.ONE, price, 0, false,
                    null, null, null, e.getKey(), shelf));
        }
        return result;
    }

    /**
     * По каждому ключу строки возвращаем ≤ продано − уже возвращено. Иначе подарок можно
     * «вернуть» за деньги, а платную строку — дороже, чем продали.
     */
    private void requireReturnable(
            StockDoc source,
            List<ReceiptLine> lines
    ) {
        Map<String, BigDecimal> sold = new HashMap<>();
        source.getLines().forEach(l -> sold.merge(
                lineKey(l.getProductId(), l.getQty(), l.getPriceMinor(), l.getDiscountMinor(), l.isGift()),
                l.getQty(), BigDecimal::add));
        Map<String, BigDecimal> returned = new HashMap<>();
        for (StockDoc ret : docService.findPostedBySourceDoc(source.getId())) {
            ret.getLines().forEach(l -> returned.merge(
                    lineKey(l.getProductId(), l.getQty(), l.getPriceMinor(), l.getDiscountMinor(), l.isGift()),
                    l.getQty(), BigDecimal::add));
        }
        Map<String, BigDecimal> requested = new HashMap<>();
        lines.forEach(l -> requested.merge(
                lineKey(l.productId(), l.qty(), l.priceMinor(), l.discountMinor(),
                        l.gift()),
                l.qty(), BigDecimal::add));
        for (var e : requested.entrySet()) {
            BigDecimal soldQty = sold.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (soldQty.signum() == 0) {
                throw new ConflictException(
                        "Строка не совпадает с чеком (товар, цена, скидка, подарок) — возврат невозможен");
            }
            BigDecimal returnable = soldQty.subtract(returned.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (e.getValue().compareTo(returnable) > 0) {
                throw new ConflictException("По чеку можно вернуть не больше "
                        + returnable.stripTrailingZeros().toPlainString());
            }
        }
    }

    /**
     * Ключ сверки с чеком. Скидка — суммой на строку, поэтому сравниваем цену единицы
     * после скидки: частичный возврат несёт её долю.
     */
    private static String lineKey(
            UUID productId,
            BigDecimal qty,
            long priceMinor,
            long discountMinor,
            boolean gift
    ) {
        return productId + "|" + priceMinor + '|'
                + CostMath.unitPriceMinor(qty, priceMinor, discountMinor) + '|' + gift;
    }

}
