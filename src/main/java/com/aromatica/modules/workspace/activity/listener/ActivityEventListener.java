package com.aromatica.modules.workspace.activity.listener;

import com.aromatica.modules.inventory.doc.event.DocActionEvent;
import com.aromatica.modules.inventory.doc.event.DocCreatedEvent;
import com.aromatica.modules.pos.shift.event.ShiftClosedEvent;
import com.aromatica.modules.pos.shift.event.ShiftOpenedEvent;
import com.aromatica.modules.workspace.activity.service.ActivityRecorder;
import com.aromatica.modules.workspace.task.event.TaskCreatedEvent;
import com.aromatica.modules.workspace.task.event.TaskDoneEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Лента только слушает события: склад, касса и задачи про неё не знают.
 * Пишем в транзакции действия: откат убирает и строку ленты.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActivityEventListener {

    ActivityRecorder recorder;

    @EventListener
    public void onDocCreated(DocCreatedEvent e) {
        recorder.docCreated(e.doc());
    }

    @EventListener
    public void onDocAction(DocActionEvent e) {
        recorder.docEvent(e.doc(), e.action(), e.actorId(), e.at());
    }

    @EventListener
    public void onShiftOpened(ShiftOpenedEvent e) {
        recorder.shiftOpened(e.shift());
    }

    @EventListener
    public void onShiftClosed(ShiftClosedEvent e) {
        recorder.shiftClosed(e.shift());
    }

    @EventListener
    public void onTaskCreated(TaskCreatedEvent e) {
        recorder.taskCreated(e.task());
    }

    @EventListener
    public void onTaskDone(TaskDoneEvent e) {
        recorder.taskDone(e.task(), e.doerId());
    }
}
