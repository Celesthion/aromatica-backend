package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.pricing.service.SellerDiscountCap;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import com.aromatica.modules.pos.sale.model.Sale;
import com.aromatica.modules.pos.sale.service.BaseConsumption;
import com.aromatica.modules.pos.sale.service.ReceiptGiftGuard;
import com.aromatica.modules.pos.sale.service.ReceiptLines;
import com.aromatica.modules.pos.sale.service.ReceiptProducts;
import com.aromatica.modules.pos.sale.service.ReceiptStockGuard;
import com.aromatica.modules.pos.sale.service.ShiftDocPoster;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Продажа на кассе. Чек — документ SALE открытой смены, создаётся и проводится одним действием
 * той же механикой, что и в админке.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SellUseCase {

    PosShiftService shiftService;
    ProductService productService;
    ReservationService reservationService;
    GiftCertificateService giftService;
    /** Общая последовательность документов смены — см. ShiftDocPoster. */
    ShiftDocPoster shiftDocs;
    /** Полка склада смены: продажа с полки. */
    WarehouseLookup warehouseLookup;
    SellerDiscountCap discountCap;
    ReceiptStockGuard stockGuard;
    ReceiptGiftGuard giftGuard;
    BaseConsumption baseConsumption;
    Clock clock;

    @Transactional
    public StockDoc handle(
            UUID shiftId,
            Sale sale,
            String login
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, login);
        UUID shelf = warehouseLookup.shelfOf(shift.getWarehouseId());
        ReceiptProducts products = ReceiptProducts.load(productService, sale.lines());
        stockGuard.requireLineWarehouses(shift.getWarehouseId(), shelf, sale.lines(), products);
        // воду дописываем сами, до проверок: продавец не может её «забыть», клиент за неё не платит
        List<ReceiptLine> lines = baseConsumption.withBaseConsumption(sale.lines(), products);
        // у комплекта скидки нет: цена фиксированная
        requireNoKitDiscount(lines, products);
        long certificateMinor = sale.certificateMinor();
        // сертификат — такой же способ оплаты, как наличные и перевод
        ReceiptLines.requirePayment(lines, sale.cashMinor(), sale.noCashMinor() + certificateMinor);
        Reservation reservation = sale.reservationId() != null
                ? reservationService.requireActiveForSale(sale.reservationId()) : null;
        if (reservation != null && !shift.getWarehouseId().equals(reservation.getWarehouseId())) {
            throw new ConflictException("Бронь оформлена на другой склад");
        }
        // ни в минус, ни из чужих броней; собственный резерв продаваемой брони не мешает
        UUID sellingReservation = reservation != null ? reservation.getId() : null;
        stockGuard.requireStockForSale(shift.getWarehouseId(), shelf, lines, products, sellingReservation);
        // строки брони можно продать по её цене, остальное — в пределах потолка от каталога
        discountCap.require(ReceiptLines.pricedLines(lines), reservation != null ? reservation.heldPrices() : Map.of());
        giftGuard.requireGiftsEarned(lines, sale.giftBaseMinor(), products);
        UUID counterpartyId = customerOf(sale, reservation);
        // гасит сертификат postingService.post, в паре с возвратом при распроведении
        UUID certificateId = certificateMinor > 0
                ? giftService.byCode(sale.certificateCode()).getId() : null;
        StockDoc posted = postShiftDoc(DocType.SALE, shift, lines,
                sale.cashMinor(), sale.noCashMinor(), certificateMinor, certificateId,
                sale.comment(), null, counterpartyId, login);
        // повтор под блокировками postingService.post: ранняя проверка шла без них
        // и не защищает от параллельного чека
        stockGuard.requireStockAfterSale(shift.getWarehouseId(), shelf, lines, products, sellingReservation);
        if (reservation != null) {
            reservationService.markSold(reservation.getId(), posted.getId());
        }
        return posted;
    }

    /** Скидка на комплект не даётся — ни строкой, ни процентом. */
    private static void requireNoKitDiscount(
            List<ReceiptLine> lines,
            ReceiptProducts products
    ) {
        lines.stream()
                .filter(l -> l.discountMinor() > 0)
                .map(l -> products.get(l.productId()))
                .filter(p -> p != null && p.hasKind(CatalogProduct.Kind.KIT))
                .findFirst()
                .ifPresent(p -> {
                    throw new BusinessRuleException("На комплект «" + p.getName()
                            + "» скидка не даётся — у него фиксированная цена");
                });
    }

    private StockDoc postShiftDoc(
            DocType type,
            PosShift shift,
            List<ReceiptLine> lines,
            Long cashMinor,
            Long noCashMinor,
            long certificateMinor,
            UUID certificateId,
            String comment,
            UUID sourceDocId,
            UUID counterpartyId,
            String login
    ) {
        StockDoc doc = StockDoc.draft(type, LocalDateTime.now(clock), shift.getWarehouseId(), comment,
                ReceiptLines.toDocLines(lines));
        doc.setCounterpartyId(counterpartyId);
        doc.setCashMinor(cashMinor);
        doc.setNoCashMinor(noCashMinor);
        doc.setCertificateMinor(certificateMinor);
        doc.setCertificateId(certificateId);
        doc.setSourceDocId(sourceDocId);
        return shiftDocs.post(doc, shift, login);
    }

    /** Покупатель чека по брони — тот, на кого она оформлена; другого подставить нельзя. */
    private static UUID customerOf(
            Sale sale,
            Reservation reservation
    ) {
        if (reservation == null || reservation.getCounterpartyId() == null) {
            return sale.counterpartyId();
        }
        if (sale.counterpartyId() != null && !sale.counterpartyId().equals(reservation.getCounterpartyId())) {
            throw new BusinessRuleException("Покупатель чека не совпадает с покупателем брони");
        }
        return reservation.getCounterpartyId();
    }
}
