package com.aromatica.modules.finance.gift.listener;

import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Сторно с кассы и распроведение из админки идут одним путём, через событие.
 * Не хватит остатка на бланке — исключение отсюда откатит и само проведение.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificatePostingListener {

    GiftCertificateService giftService;

    @EventListener
    public void onPosted(DocPostedEvent event) {
        StockDoc doc = event.doc();
        if (doc.getCertificateId() != null && doc.getCertificateMinor() > 0) {
            giftService.redeemForDoc(doc.getCertificateId(), doc.getCertificateMinor(), doc.getId());
        }
    }

    @EventListener
    public void onUnposted(DocUnpostedEvent event) {
        giftService.revertForDoc(event.doc().getId());
    }
}
