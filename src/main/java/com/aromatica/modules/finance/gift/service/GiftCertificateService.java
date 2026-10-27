package com.aromatica.modules.finance.gift.service;

import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.model.GiftCertificateUse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Подарочные сертификаты: завоз бланков, продажа, погашение.
 * Продажа — аванс, а не выручка: складских документов нет, деньги числятся на сертификате.
 */
public interface GiftCertificateService {

    /** Если хоть один код уже заведён, отклоняет всю пачку. */
    List<GiftCertificate> importBatch(List<String> codes, long nominalMinor, String comment, UUID userId);

    /** Коды выдаёт система: 12 случайных цифр с контрольной по Луну. */
    List<GiftCertificate> generateBatch(int count, long nominalMinor, String comment, UUID userId);

    /** Ловить исключение из byCode нельзя: оно помечает транзакцию rollback-only. */
    Optional<GiftCertificate> find(String code);

    GiftCertificate byCode(String code);

    /** Покупатель обязателен: без него не подтвердить покупку, если бланк потеряют. */
    GiftCertificate sell(String code, UUID shiftId, long cashMinor, long noCashMinor, UUID buyerId, UUID userId);

    /** Не больше остатка: сдачу деньгами не выдаём. Пара — {@link #revertForDoc(UUID)}. */
    GiftCertificate redeemForDoc(UUID certificateId, long amountMinor, UUID docId);

    /**
     * Возвращает чистую сумму погашений чека по журналу: при перепроведении
     * построчный откат вернул бы старое погашение дважды.
     */
    void revertForDoc(UUID docId);

    GiftCertificate block(UUID id, String comment, UUID userId);

    Stats stats();

    record Stats(
            long liabilityMinor,
            long inStock,
            long total
    ) {}

    List<GiftCertificateUse> history(UUID certificateId);

    List<GiftCertificate> soldInShift(UUID shiftId);

    /** Положительным числом. */
    long redeemedForDocs(List<UUID> docIds);
}
