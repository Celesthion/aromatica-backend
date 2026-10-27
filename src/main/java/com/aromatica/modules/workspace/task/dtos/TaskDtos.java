package com.aromatica.modules.workspace.task.dtos;

import com.aromatica.modules.workspace.task.model.Task;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {}

    public record TaskItemDto(
            UUID id,
            String text,
            boolean done,
            int position
    ) {}

    public record TaskDto(
            UUID id,
            String title,
            String description,
            UUID assigneeId,
            String assigneeName,
            UUID createdBy,
            String createdByName,
            Task.Status status,
            LocalDate dueDate,
            LocalDateTime createdAt,
            LocalDateTime doneAt,
            List<TaskItemDto> items
    ) {}

    public record CreateTaskRequest(

            @NotBlank(message = "У задачи должно быть название")
            String title,

            String description,

            UUID assigneeId,

            LocalDate dueDate,

            List<String> items
    ) {}

    public record UpdateTaskRequest(
            String title,
            String description,
            UUID assigneeId,
            LocalDate dueDate
    ) {}

    public record TaskItemRequest(

            @NotBlank(message = "Пустой пункт добавить нельзя")
            String text
    ) {}

    public record AssigneeOption(
            UUID id,
            String name
    ) {}
}
