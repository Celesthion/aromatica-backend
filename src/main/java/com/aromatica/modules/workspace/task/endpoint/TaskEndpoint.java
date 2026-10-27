package com.aromatica.modules.workspace.task.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.AssigneeOption;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.CreateTaskRequest;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskDto;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.UpdateTaskRequest;
import com.aromatica.modules.workspace.task.search.TaskSearchRequest;

import java.util.List;
import java.util.UUID;

/** Задачи и чек-листы на уровне DTO. Админ видит все, продавец свои. */
public interface TaskEndpoint {

    PageResponse<TaskDto> search(TaskSearchRequest request);

    TaskDto create(CreateTaskRequest request);

    TaskDto update(UUID id, UpdateTaskRequest request);

    void delete(UUID id);

    TaskDto setDone(UUID id, boolean done);

    TaskDto addItem(UUID id, String text);

    TaskDto toggleItem(UUID itemId);

    TaskDto removeItem(UUID itemId);

    List<AssigneeOption> assignees();
}
