package com.aromatica.modules.finance.money.model;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Долг поставщику: неоплаченное за вычетом возвратов.
 *
 * @param counterpartyId поставщик; null — документы без контрагента
 * @param docs           проведённые неоплаченные приёмки и возвраты, свежие сверху
 */
public record Debt(

        @Nullable
        UUID counterpartyId,

        long debtMinor,

        List<StockDoc> docs
) {
}
