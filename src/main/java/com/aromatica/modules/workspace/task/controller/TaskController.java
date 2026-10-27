package com.aromatica.modules.workspace.task.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.AssigneeOption;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.CreateTaskRequest;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskDto;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskItemRequest;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.UpdateTaskRequest;
import com.aromatica.modules.workspace.task.endpoint.TaskEndpoint;
import com.aromatica.modules.workspace.task.search.TaskSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Задачи", description = "Задачи и чек-листы. Администратор видит все задачи, остальные роли — только свои.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER', 'MERCHANDISER', 'ONLINE_MANAGER')")
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskController {

    TaskEndpoint taskEndpoint;

    @Operation(
            summary = "Поиск задач",
            description = "Страницы; по умолчанию открытые сверху и ближайший срок первым. Сотрудник видит назначенные ему и созданные им"
    )
    @PostMapping("/search")
    public PageResponse<TaskDto> search(@RequestBody TaskSearchRequest request) {
        return taskEndpoint.search(request);
    }

    @Operation(summary = "Создать задачу")
    @PostMapping
    public TaskDto create(@Valid @RequestBody CreateTaskRequest request) {
        return taskEndpoint.create(request);
    }

    @Operation(summary = "Изменить задачу")
    @PutMapping("/{id}")
    public TaskDto update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return taskEndpoint.update(id, request);
    }

    @Operation(summary = "Удалить задачу")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        taskEndpoint.delete(id);
    }

    @Operation(summary = "Отметить задачу выполненной")
    @PostMapping("/{id}/done")
    public TaskDto markDone(@PathVariable UUID id) {
        return taskEndpoint.setDone(id, true);
    }

    @Operation(summary = "Вернуть задачу в работу")
    @PostMapping("/{id}/reopen")
    public TaskDto reopen(@PathVariable UUID id) {
        return taskEndpoint.setDone(id, false);
    }

    @Operation(summary = "Добавить пункт чек-листа")
    @PostMapping("/{id}/items")
    public TaskDto addItem(
            @PathVariable UUID id,
            @Valid @RequestBody TaskItemRequest request
    ) {
        return taskEndpoint.addItem(id, request.text());
    }

    @Operation(summary = "Переключить пункт чек-листа")
    @PostMapping("/items/{itemId}/toggle")
    public TaskDto toggleItem(@PathVariable UUID itemId) {
        return taskEndpoint.toggleItem(itemId);
    }

    @Operation(summary = "Удалить пункт чек-листа")
    @DeleteMapping("/items/{itemId}")
    public TaskDto removeItem(@PathVariable UUID itemId) {
        return taskEndpoint.removeItem(itemId);
    }

    @Operation(summary = "Возможные исполнители", description = "Кого можно назначить исполнителем задачи; нужно в основном администратору.")
    @GetMapping("/assignees")
    public List<AssigneeOption> assignees() {
        return taskEndpoint.assignees();
    }
}
