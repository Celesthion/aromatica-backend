package com.aromatica.modules.workspace.activity.service;

import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.workspace.task.model.Task;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Пишет ленту в транзакции действия: откат действия откатит и запись.
 * Подписи и разделы — из {@link ActivityClassifier}; имя актора подставляется при чтении.
 */
public interface ActivityRecorder {

    void docCreated(StockDoc doc);

    void docEvent(StockDoc doc, DocEvent.Action action, UUID actorId, LocalDateTime at);

    void shiftOpened(PosShift shift);

    void shiftClosed(PosShift shift);

    void taskCreated(Task task);

    /** doerId — тот, кто отметил задачу выполненной. */
    void taskDone(Task task, UUID doerId);
}
