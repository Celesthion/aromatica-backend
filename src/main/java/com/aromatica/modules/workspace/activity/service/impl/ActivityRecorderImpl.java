package com.aromatica.modules.workspace.activity.service.impl;

import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.workspace.activity.model.ActivityLog;
import com.aromatica.modules.workspace.activity.repository.ActivityLogRepository;
import com.aromatica.modules.workspace.activity.service.ActivityClassifier;
import com.aromatica.modules.workspace.activity.service.ActivityRecorder;
import com.aromatica.modules.workspace.task.model.Task;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActivityRecorderImpl implements ActivityRecorder {

    ActivityLogRepository repository;
    Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void docCreated(StockDoc doc) {
        save(doc.getCreatedAt(), doc.getCreatedBy(), ActivityClassifier.docSection(doc.getDocType()),
                "doc", "Создание", "stock_doc", doc.getId(),
                ActivityClassifier.docTitle(doc.getDocType(), doc.getNumber()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void docEvent(
            StockDoc doc,
            DocEvent.Action action,
            UUID actorId,
            LocalDateTime at
    ) {
        if (doc == null) {
            return;
        }
        save(at, actorId, ActivityClassifier.eventSection(action, doc.getDocType()), "doc",
                ActivityClassifier.eventAction(action), "stock_doc", doc.getId(),
                ActivityClassifier.docTitle(doc.getDocType(), doc.getNumber()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void shiftOpened(PosShift shift) {
        save(shift.getOpenedAt(), shift.getOpenedBy(), "pos", "shift", "Открытие смены",
                "pos_shift", shift.getId(), "Смена №" + shift.getNumber());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void shiftClosed(PosShift shift) {
        save(shift.getClosedAt(), shift.getClosedBy(), "pos", "shift", "Закрытие смены",
                "pos_shift", shift.getId(), "Смена №" + shift.getNumber());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void taskCreated(Task task) {
        save(task.getCreatedAt(), task.getCreatedBy(), "task", "task", "Постановка задачи",
                "task", task.getId(), "«" + task.getTitle() + "»");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void taskDone(
            Task task,
            UUID doerId
    ) {
        save(task.getDoneAt(), doerId, "task", "task", "Выполнение задачи",
                "task", task.getId(), "«" + task.getTitle() + "»");
    }

    private void save(
            LocalDateTime at,
            UUID actorId,
            String section,
            String kind,
            String action,
            String entityType,
            UUID entityId,
            String title
    ) {
        repository.save(new ActivityLog(
                at != null ? at : LocalDateTime.now(clock),
                actorId, section, kind, action, entityType, entityId, title));
    }
}
