package com.aromatica.modules.workspace.task.event;

import com.aromatica.modules.workspace.task.model.Task;

public record TaskCreatedEvent(
        Task task
) {
}
