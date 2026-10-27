package com.aromatica.modules.workspace.activity.service;

import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.DocType;

/** Правила ленты: раздел и подписи. */
public final class ActivityClassifier {

    private ActivityClassifier() {}

    /** Продажа на кассе называется чеком. */
    public static String docTitle(
            DocType type,
            long number
    ) {
        String noun = switch (type) {
            case SUPPLY -> "Приёмка";
            case SALE -> "Чек";
            case WRITE_OFF -> "Списание";
            case SURPLUS -> "Оприходование";
            case INVENTORY -> "Инвентаризация";
            case MOVE -> "Перемещение";
            case CONVERT -> "Вскрытие";
            case RETURN -> "Возврат";
            case RETURN_SUPPLY -> "Возврат поставщику";
            case ASSEMBLY -> "Сборка";
            case DISASSEMBLY -> "Разборка";
        };
        return noun + " №" + number;
    }

    /** Продажи и возвраты покупателю — касса, всё остальное — склад. */
    public static String docSection(DocType type) {
        return switch (type) {
            case SALE, RETURN -> "pos";
            default -> "warehouse";
        };
    }

    /** Оплата идёт в деньги, сторно в кассу, остальные события по типу документа. */
    public static String eventSection(
            DocEvent.Action action,
            DocType type
    ) {
        return switch (action) {
            case PAY -> "money";
            case VOID -> "pos";
            default -> type != null ? docSection(type) : "warehouse";
        };
    }

    /** Подпись события без рода: «Проведение», а не «провёл/провела». */
    public static String eventAction(DocEvent.Action action) {
        return switch (action) {
            case POST -> "Проведение";
            case UNPOST -> "Распроведение";
            case CANCEL -> "Отмена";
            case VOID -> "Сторно";
            case PAY -> "Оплата";
        };
    }
}
