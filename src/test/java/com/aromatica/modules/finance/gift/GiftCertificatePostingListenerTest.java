package com.aromatica.modules.finance.gift;

import com.aromatica.modules.finance.gift.listener.GiftCertificatePostingListener;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.inventory.doc.event.DocPostedEvent;
import com.aromatica.modules.inventory.doc.event.DocUnpostedEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Сертификат ведёт себя как деньги: проведение списывает, распроведение
 * возвращает. Иначе отмена чека из админки, а не сторно на кассе, оставила бы
 * остаток списанным.
 */
@ExtendWith(MockitoExtension.class)
class GiftCertificatePostingListenerTest {

    @Mock
    private GiftCertificateService giftService;

    private StockDoc doc(
            UUID certificateId,
            long certificateMinor
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setCertificateId(certificateId);
        doc.setCertificateMinor(certificateMinor);
        return doc;
    }

    @Test
    void posted_redeemsCertificateForDoc() {
        UUID certificate = UUID.randomUUID();
        StockDoc doc = doc(certificate, 200_00);

        new GiftCertificatePostingListener(giftService).onPosted(new DocPostedEvent(doc));

        verify(giftService).redeemForDoc(certificate, 200_00, doc.getId());
    }

    /** Чек без сертификата бланки не трогает. */
    @Test
    void posted_withoutCertificate_doesNothing() {
        new GiftCertificatePostingListener(giftService).onPosted(new DocPostedEvent(doc(null, 0)));
        verify(giftService, never()).redeemForDoc(any(), anyLong(), any());
    }

    @Test
    void unposted_revertsWhateverWasRedeemed() {
        StockDoc doc = doc(UUID.randomUUID(), 200_00);
        new GiftCertificatePostingListener(giftService).onUnposted(new DocUnpostedEvent(doc));
        verify(giftService).revertForDoc(doc.getId());
    }
}
