package com.aromatica.modules.finance.gift;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.gift.model.GiftCertificate.Status;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.model.GiftCertificateUse;
import com.aromatica.modules.finance.gift.repository.GiftCertificateRepository;
import com.aromatica.modules.finance.gift.repository.GiftCertificateUseRepository;
import com.aromatica.modules.finance.gift.service.impl.GiftCertificateServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Денежная логика сертификатов. Здесь легко потерять или создать деньги из
 * воздуха, поэтому проверяем границы: двойную продажу, перерасход остатка
 * и возврат сверх погашенного.
 */
@ExtendWith(MockitoExtension.class)
class GiftCertificateServiceTest {

    @Mock
    private GiftCertificateRepository repository;
    @Mock
    private GiftCertificateUseRepository useRepository;
    /** Через него бланк перечитывается под блокировкой, в юнит-тесте это заглушка. */
    @Mock
    private EntityManager entityManager;

    private GiftCertificateServiceImpl service;
    private final List<GiftCertificateUse> uses = new ArrayList<>();

    private static final long NOMINAL = 500_000; // 5000 сом
    private static final UUID BUYER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // аудитор в юнит-тестах пустой: кто сделал движение, здесь не проверяем
        service = new GiftCertificateServiceImpl(repository, useRepository, Optional::empty, entityManager, Clock.systemDefaultZone());
        uses.clear();
        lenient().when(useRepository.save(any())).thenAnswer(inv -> {
            uses.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private GiftCertificate blank(
            Status status,
            long balance
    ) {
        GiftCertificate c = new GiftCertificate();
        c.setId(UUID.randomUUID());
        c.setCode("GC-001");
        c.setNominalMinor(NOMINAL);
        c.setBalanceMinor(balance);
        c.setStatus(status);
        lenient().when(repository.findByCode("GC-001")).thenReturn(Optional.of(c));
        lenient().when(repository.require(eq(c.getId()), anyString())).thenReturn(c);
        return c;
    }

    @Test
    void importBatch_rejectsCodesAlreadyKnown() {
        GiftCertificate existing = new GiftCertificate();
        existing.setCode("GC-001");
        when(repository.findByCodeIn(anyList())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.importBatch(List.of("GC-001", "GC-002"), NOMINAL, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("уже заведены");
    }

    @Test
    void importBatch_dropsBlanksAndDuplicatesInsideBatch() {
        when(repository.findByCodeIn(anyList())).thenReturn(List.of());
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<GiftCertificate> batch =
                service.importBatch(List.of("GC-1", " GC-1 ", "", "  ", "GC-2"), NOMINAL, null, null);

        assertThat(batch).extracting(GiftCertificate::getCode).containsExactly("GC-1", "GC-2");
        // непроданный бланк ещё не обязательство, но номинал уже известен
        assertThat(batch).allMatch(c -> c.getStatus() == Status.NEW);
    }

    /**
     * Отсутствие кода — обычный ответ, а не исключение. Даже если check() на кассе
     * поймает исключение, транзакция уже помечена rollback-only и запрос упадёт с 500.
     */
    @Test
    void find_returnsEmptyForUnknownCode() {
        when(repository.findByCode("НЕТ-ТАКОГО")).thenReturn(Optional.empty());
        assertThat(service.find("НЕТ-ТАКОГО")).isEmpty();
    }

    @Test
    void find_trimsCodeAndTreatsNullAsMiss() {
        blank(Status.ACTIVE, NOMINAL);
        assertThat(service.find("  GC-001  ")).isPresent();
        when(repository.findByCode("")).thenReturn(Optional.empty());
        assertThat(service.find(null)).isEmpty();
    }

    @Test
    void sell_activatesBlankAndRemembersPayment() {
        GiftCertificate c = blank(Status.NEW, NOMINAL);
        UUID shiftId = UUID.randomUUID();

        service.sell("GC-001", shiftId, 200_000, 300_000, BUYER, null);

        assertThat(c.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(c.getBalanceMinor()).isEqualTo(NOMINAL);
        assertThat(c.getSoldShiftId()).isEqualTo(shiftId);
        assertThat(c.getSoldCashMinor()).isEqualTo(200_000);
        assertThat(c.getSoldNoCashMinor()).isEqualTo(300_000);
        assertThat(c.getBuyerId()).isEqualTo(BUYER);
    }

    @Test
    void sell_requiresPaymentEqualToNominal() {
        blank(Status.NEW, NOMINAL);
        assertThatThrownBy(() -> service.sell("GC-001", UUID.randomUUID(), 100_000, 0, BUYER, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не сходится");
    }

    /** Один бланк — одна продажа: иначе тот же код пробили бы дважды. */
    @Test
    void sell_rejectsSecondSaleOfSameBlank() {
        blank(Status.ACTIVE, NOMINAL);
        assertThatThrownBy(() -> service.sell("GC-001", UUID.randomUUID(), NOMINAL, 0, BUYER, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уже продан");
    }

    @Test
    void sell_rejectsBlockedBlank() {
        blank(Status.BLOCKED, NOMINAL);
        assertThatThrownBy(() -> service.sell("GC-001", UUID.randomUUID(), NOMINAL, 0, BUYER, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("заблокирован");
    }

    @Test
    void redeem_spendsPartAndKeepsRest() {
        GiftCertificate c = blank(Status.ACTIVE, NOMINAL);

        service.redeemForDoc(c.getId(), 300_000, UUID.randomUUID());

        assertThat(c.getBalanceMinor()).isEqualTo(200_000);
        assertThat(c.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(uses).singleElement()
                .extracting(GiftCertificateUse::getAmountMinor).isEqualTo(-300_000L);
    }

    @Test
    void redeem_marksUsedWhenBalanceHitsZero() {
        GiftCertificate c = blank(Status.ACTIVE, 200_000);
        service.redeemForDoc(c.getId(), 200_000, UUID.randomUUID());
        assertThat(c.getStatus()).isEqualTo(Status.USED);
        assertThat(c.getBalanceMinor()).isZero();
    }

    /** Сдачу деньгами не выдаём — списать больше остатка нельзя. */
    @Test
    void redeem_rejectsMoreThanBalance() {
        GiftCertificate c = blank(Status.ACTIVE, 200_000);
        assertThatThrownBy(() -> service.redeemForDoc(c.getId(), 300_000, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не хватает");
    }

    @Test
    void redeem_rejectsUnsoldBlank() {
        GiftCertificate c = blank(Status.NEW, NOMINAL);
        assertThatThrownBy(() -> service.redeemForDoc(c.getId(), 100_000, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не продан");
    }

    @Test
    void redeem_rejectsBlockedBlank() {
        GiftCertificate c = blank(Status.BLOCKED, NOMINAL);
        assertThatThrownBy(() -> service.redeemForDoc(c.getId(), 100_000, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("заблокирован");
    }

    @Test
    void revert_putsMoneyBackOnCertificate() {
        GiftCertificate c = blank(Status.USED, 0);
        UUID doc = UUID.randomUUID();
        journal(doc, use(c.getId(), doc, -300_000));

        service.revertForDoc(doc);

        assertThat(c.getBalanceMinor()).isEqualTo(300_000);
        assertThat(c.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(uses).singleElement()
                .extracting(GiftCertificateUse::getAmountMinor).isEqualTo(300_000L);
        // остаток меняется под блокировкой строки, как при погашении
        verify(entityManager).refresh(c, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Чек можно распровести и провести снова. Откат должен возвращать чистую
     * сумму по чеку: построчный откат вернул бы уже возвращённое погашение
     * второй раз и раздул бы остаток сертификата.
     */
    @Test
    void revert_isIdempotentAcrossRepost() {
        GiftCertificate c = blank(Status.ACTIVE, NOMINAL);
        UUID doc = UUID.randomUUID();
        // погасили, распровели (вернули), провели снова: в журнале три строки
        journal(doc,
                use(c.getId(), doc, -300_000),
                use(c.getId(), doc, 300_000),
                use(c.getId(), doc, -300_000));
        c.setBalanceMinor(200_000);

        service.revertForDoc(doc);

        assertThat(c.getBalanceMinor()).isEqualTo(NOMINAL);
        assertThat(uses).singleElement()
                .extracting(GiftCertificateUse::getAmountMinor).isEqualTo(300_000L);
    }

    /** Всё уже возвращено — повторный откат ничего не делает. */
    @Test
    void revert_doesNothingWhenAlreadySettled() {
        GiftCertificate c = blank(Status.ACTIVE, NOMINAL);
        UUID doc = UUID.randomUUID();
        journal(doc, use(c.getId(), doc, -300_000), use(c.getId(), doc, 300_000));

        service.revertForDoc(doc);

        assertThat(c.getBalanceMinor()).isEqualTo(NOMINAL);
        assertThat(uses).isEmpty();
    }

    /**
     * Блокировку снимает только человек. Иначе сторно чека молча оживляло бы
     * бланк, заблокированный как утерянный.
     */
    @Test
    void revert_keepsBlockedBlankBlocked() {
        GiftCertificate c = blank(Status.BLOCKED, 0);
        UUID doc = UUID.randomUUID();
        journal(doc, use(c.getId(), doc, -300_000));

        service.revertForDoc(doc);

        assertThat(c.getBalanceMinor()).isEqualTo(300_000);
        assertThat(c.getStatus()).isEqualTo(Status.BLOCKED);
    }

    /** Через возвраты нельзя надуть сертификат больше номинала — это были бы деньги из воздуха. */
    @Test
    void revert_cannotExceedNominal() {
        GiftCertificate c = blank(Status.ACTIVE, 400_000);
        UUID doc = UUID.randomUUID();
        journal(doc, use(c.getId(), doc, -200_000));

        assertThatThrownBy(() -> service.revertForDoc(doc))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("больше, чем было погашено");
    }

    private void journal(
            UUID docId,
            GiftCertificateUse... rows
    ) {
        when(useRepository.findByDocIdIn(List.of(docId))).thenReturn(List.of(rows));
    }

    private GiftCertificateUse use(
            UUID certificateId,
            UUID docId,
            long amountMinor
    ) {
        GiftCertificateUse u = new GiftCertificateUse();
        u.setCertificateId(certificateId);
        u.setDocId(docId);
        u.setAmountMinor(amountMinor);
        return u;
    }
}
