package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.doc.model.StockDoc;

/**
 * Сверка оплаты чека с итогом; нет разбивки — всё наличными. Вызывается до события о проведении:
 * деньги проводят разбивку как есть. Сертификат в сверке, хотя движением денег не станет.
 */
final class ReceiptPayment {

    private ReceiptPayment() {
    }

    static void settle(StockDoc doc) {
        long split = doc.getCashMinor() + doc.getNoCashMinor() + doc.getCertificateMinor();
        if (split == 0) {
            doc.setCashMinor(doc.getSumMinor());
            return;
        }
        if (split != doc.getSumMinor()) {
            throw new ConflictException(
                    "Разбивка оплаты (" + split + ") не сходится с итогом документа (" + doc.getSumMinor() + ")");
        }
    }
}
