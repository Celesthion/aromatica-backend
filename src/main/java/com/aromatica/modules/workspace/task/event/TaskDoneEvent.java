package com.aromatica.modules.workspace.task.event;

import com.aromatica.modules.workspace.task.model.Task;

import java.util.UUID;

/** Задача выполнена; doerId — кто отметил. */
public record TaskDoneEvent(
        Task task,
        UUID doerId
) {
}
