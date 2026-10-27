package com.aromatica.modules.workspace.activity;

import com.aromatica.modules.inventory.doc.event.DocActionEvent;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.pos.shift.event.ShiftOpenedEvent;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.workspace.activity.listener.ActivityEventListener;
import com.aromatica.modules.workspace.activity.service.ActivityRecorder;
import com.aromatica.modules.workspace.task.event.TaskDoneEvent;
import com.aromatica.modules.workspace.task.model.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/** Лента слушает чужие события и пишет их через один рекордер. */
@ExtendWith(MockitoExtension.class)
class ActivityEventListenerTest {

    @Mock
    private ActivityRecorder recorder;

    @Test
    void listener_forwardsEventsToRecorder() {
        ActivityEventListener listener = new ActivityEventListener(recorder);
        PosShift shift = new PosShift();
        Task task = new Task();
        StockDoc doc = new StockDoc();
        UUID actor = UUID.randomUUID();
        LocalDateTime at = LocalDateTime.now();

        listener.onShiftOpened(new ShiftOpenedEvent(shift));
        listener.onTaskDone(new TaskDoneEvent(task, actor));
        listener.onDocAction(new DocActionEvent(doc, DocEvent.Action.POST, actor, at));

        verify(recorder).shiftOpened(shift);
        verify(recorder).taskDone(task, actor);
        verify(recorder).docEvent(doc, DocEvent.Action.POST, actor, at);
    }
}
