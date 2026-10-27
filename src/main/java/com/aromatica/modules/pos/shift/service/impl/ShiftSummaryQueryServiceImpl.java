package com.aromatica.modules.pos.shift.service.impl;

import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.SellerLine;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.ShiftSummaryQueryService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftSummaryQueryServiceImpl implements ShiftSummaryQueryService {

    DocService docService;
    UserDirectory users;
    GiftCertificateService giftService;

    /**
     * Через одну кассу работают несколько человек, поэтому вклад каждого продавца считаем отдельно.
     */
    @Override
    public ShiftSummary of(PosShift shift) {
        ShiftTotals totals = new ShiftTotals();
        docService.findByShiftId(shift.getId()).stream()
                .filter(StockDoc::isPosted)
                .forEach(totals::add);
        CertificateTotals certificates = certificates(shift, totals.saleDocIds);

        return new ShiftSummary(totals.receipts, totals.revenue, totals.cash, totals.noCash,
                totals.returns, totals.returnsSum,
                shift.getOpeningCashMinor() + totals.cash + certificates.cashMinor(),
                averageMinor(totals.revenue, totals.receipts),
                totals.discounts, totals.gifts, totals.giftsCost,
                certificates.count(), certificates.soldMinor(), certificates.redeemedMinor(),
                totals.writeOffMl, totals.writeOffCost, perSeller(totals.bySeller));
    }

    @Override
    public long expectedCashMinor(PosShift shift) {
        return of(shift).expectedCashMinor();
    }

    /**
     * За сертификат заплатили, деньги в ящике, но товар не отгружен, так что выручки нет.
     * При погашении наоборот: выручка есть, денег нет.
     */
    private CertificateTotals certificates(
            PosShift shift,
            List<UUID> saleDocIds
    ) {
        List<GiftCertificate> sold = giftService.soldInShift(shift.getId());
        long cash = sold.stream().mapToLong(GiftCertificate::getSoldCashMinor).sum();
        long noCash = sold.stream().mapToLong(GiftCertificate::getSoldNoCashMinor).sum();
        return new CertificateTotals(sold.size(), cash + noCash, cash, giftService.redeemedForDocs(saleDocIds));
    }

    private List<SellerLine> perSeller(Map<UUID, SellerTotals> bySeller) {
        Map<UUID, String> logins = users.logins(bySeller.keySet());
        Map<UUID, String> names = users.names(bySeller.keySet());
        return bySeller.entrySet().stream()
                .map(e -> new SellerLine(
                        e.getKey() != null ? logins.getOrDefault(e.getKey(), "?") : "?",
                        e.getKey() != null ? names.getOrDefault(e.getKey(), "?") : "?",
                        e.getValue().receipts, e.getValue().revenueMinor, e.getValue().returnsMinor,
                        e.getValue().writeOffMl, e.getValue().writeOffCostMinor))
                .sorted(Comparator.comparingLong(SellerLine::revenueMinor).reversed())
                .toList();
    }

    /** Средний чек с округлением до тыйына, без double: сумма в тыйынах может быть большой. */
    private static long averageMinor(
            long totalMinor,
            int count
    ) {
        return count > 0 ? (totalMinor + count / 2) / count : 0;
    }

    /** Итоги одного продавца за смену. */
    private static final class SellerTotals {
        int receipts;
        long revenueMinor;
        long returnsMinor;
        BigDecimal writeOffMl = BigDecimal.ZERO;
        long writeOffCostMinor;
    }

    /** Итоги смены по документам; продажа, списание и возврат раскладываются каждый по-своему. */
    private static final class ShiftTotals {
        int receipts;
        long revenue;
        long cash;
        long noCash;
        int returns;
        long returnsSum;
        long discounts;
        int gifts;
        long giftsCost;
        BigDecimal writeOffMl = BigDecimal.ZERO;
        long writeOffCost;
        final Map<UUID, SellerTotals> bySeller = new LinkedHashMap<>();
        final List<UUID> saleDocIds = new ArrayList<>();

        void add(StockDoc doc) {
            SellerTotals seller = bySeller.computeIfAbsent(doc.getCreatedBy(), k -> new SellerTotals());
            switch (doc.getDocType()) {
                case SALE -> addSale(doc, seller);
                case WRITE_OFF -> addWriteOff(doc, seller);
                case RETURN -> addReturn(doc, seller);
                default -> {
                    // другие документы в смене не влияют на X-отчёт
                }
            }
        }

        private void addSale(
                StockDoc doc,
                SellerTotals seller
        ) {
            receipts++;
            revenue += doc.getSumMinor();
            cash += doc.getCashMinor();
            noCash += doc.getNoCashMinor();
            seller.receipts++;
            seller.revenueMinor += doc.getSumMinor();
            saleDocIds.add(doc.getId());
            for (StockDocLine line : doc.getLines()) {
                if (line.isGift()) {
                    // клиенту подарок бесплатен, но склад теряет себестоимость
                    gifts++;
                    giftsCost += line.costSumMinor();
                } else {
                    discounts += line.getDiscountMinor();
                }
            }
        }

        /** «Не хватило» при наливе: выручки нет, только убыток по себестоимости. */
        private void addWriteOff(
                StockDoc doc,
                SellerTotals seller
        ) {
            for (StockDocLine line : doc.getLines()) {
                long cost = line.costSumMinor();
                writeOffMl = writeOffMl.add(line.getQty());
                writeOffCost += cost;
                seller.writeOffMl = seller.writeOffMl.add(line.getQty());
                seller.writeOffCostMinor += cost;
            }
        }

        /** Возврат вычитаем из того способа оплаты, которым отдали деньги. */
        private void addReturn(
                StockDoc doc,
                SellerTotals seller
        ) {
            returns++;
            returnsSum += doc.getSumMinor();
            cash -= doc.getCashMinor();
            noCash -= doc.getNoCashMinor();
            seller.returnsMinor += doc.getSumMinor();
        }
    }

    /** Сертификаты смены: проданные (всего и наличными) и погашенные в её чеках. */
    private record CertificateTotals(
            int count,
            long soldMinor,
            long cashMinor,
            long redeemedMinor
    ) {}
}
