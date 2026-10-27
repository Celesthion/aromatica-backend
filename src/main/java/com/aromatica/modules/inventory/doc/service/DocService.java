package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Черновики складских документов: создание, правка, отмена; поиск — в DocSearchService.
 * Другие модули работают с StockDocRepository только через этот сервис.
 */
public interface DocService {

    /** id, номер и статус ставит сервис, строки проверяет и нумерует; прочие поля сохраняются как есть. */
    StockDoc create(StockDoc doc);

    /** Черновик целиком заменяется присланным: шапка, оплата и строки. */
    StockDoc update(UUID id, StockDoc changes);

    void cancel(UUID id);

    StockDoc find(UUID id);

    /** Блокировка строки до конца транзакции вызывающего — для решений по текущему состоянию (возврат, сторно). */
    StockDoc findForUpdate(UUID id);

    /** Чеки кассовой смены (SALE/RETURN с shift_id), свежие сверху. */
    List<StockDoc> findByShiftId(UUID shiftId);

    /** Проведённые чеки и возвраты смены, свежие сверху. */
    List<StockDoc> findPostedReceiptsOfShift(UUID shiftId);

    /** Проведённые возвраты, ссылающиеся на конкретный чек продажи. */
    List<StockDoc> findPostedBySourceDoc(UUID sourceDocId);

    /** Число строк документов одним запросом: id → count; у документа без строк ключа нет. */
    Map<UUID, Integer> lineCounts(Collection<UUID> ids);

    List<StockDoc> findAllById(Collection<UUID> ids);

    /** Последние проведённые продажи контрагенту — история покупок на кассе. */
    List<StockDoc> lastSalesOf(UUID counterpartyId, int limit);

    /** Долг поставщику: контрагент (null — документы без него) и неоплаченное за вычетом возвратов. */
    record SupplierDebt(

            @Nullable
            UUID counterpartyId,

            long debtMinor
    ) {
    }

    /** Ненулевые долги поставщикам, посчитанные в базе. */
    List<SupplierDebt> supplierDebts();

    /**
     * Проведённые неоплаченные приёмки и возвраты поставщику этих контрагентов;
     * withoutCounterparty — и документы без контрагента.
     */
    List<StockDoc> findUnpaidSupplierDocs(Collection<UUID> counterpartyIds, boolean withoutCounterparty);
}
