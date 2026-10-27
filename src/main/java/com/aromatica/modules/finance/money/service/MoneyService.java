package com.aromatica.modules.finance.money.service;

import com.aromatica.modules.finance.money.model.Debt;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Счета, журнал движений и автодвижения из документов — в транзакции проведения.
 * SALE без разбивки нал/перевод целиком идёт в наличные; SUPPLY — расход, если указан счёт оплаты.
 */
public interface MoneyService {

    List<MoneyAccount> activeAccounts();

    Map<UUID, Long> balancesByAccount();

    /** Архивные тоже: они встречаются в журнале. */
    Map<UUID, String> accountNames();

    MoneyAccount findAccount(UUID id);

    Slice<MoneyMovement> movements(UUID accountId, int page, int size);

    MoneyMovement createManual(UUID accountId, long amountMinor, LocalDateTime moment, String comment);

    /** Автодвижения живут вместе с документом. */
    void deleteManual(long id);

    /** В транзакции вызывающего проведения. */
    void onPost(StockDoc doc);

    /** В транзакции вызывающего распроведения. */
    void onUnpost(StockDoc doc);

    void transfer(UUID fromAccountId, UUID toAccountId, long amountMinor, String comment);

    /** Так касса проводит недостачу или излишек при закрытии смены. */
    void manualAdjustment(MoneyAccount.Type type, long amountMinor, String comment);

    /** Оплата проведённой приёмки или возврата поставщику. Документ меняется — транзакция и flush на вызывающем. */
    void payDoc(StockDoc doc, UUID accountId);

    List<Debt> debts();
}
