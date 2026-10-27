package com.aromatica.modules.workspace.task.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.AssigneeOption;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.CreateTaskRequest;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskDto;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.UpdateTaskRequest;
import com.aromatica.modules.workspace.task.endpoint.TaskEndpoint;
import com.aromatica.modules.workspace.task.mapper.TaskMapper;
import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.search.TaskSearchRequest;
import com.aromatica.modules.workspace.task.service.TaskSearchService;
import com.aromatica.modules.workspace.task.service.TaskService;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskEndpointImpl implements TaskEndpoint {

    TaskService taskService;
    TaskSearchService searchService;
    PageMapper pageMapper;
    UserDirectory users;
    TaskMapper taskMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskDto> search(TaskSearchRequest request) {
        Page<Task> page = searchService.search(CurrentUser.login(), request);
        return pageMapper.toResponse(page, taskMapper.toDtos(page.getContent()));
    }

    @Override
    @Transactional
    public TaskDto create(CreateTaskRequest request) {
        return taskMapper.toDto(taskService.create(taskMapper.toEntity(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public TaskDto update(
            UUID id,
            UpdateTaskRequest request
    ) {
        return taskMapper.toDto(taskService.update(id, taskMapper.toEntity(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        taskService.delete(id, CurrentUser.login());
    }

    @Override
    @Transactional
    public TaskDto setDone(
            UUID id,
            boolean done
    ) {
        return taskMapper.toDto(taskService.setDone(id, done, CurrentUser.login()));
    }

    @Override
    @Transactional
    public TaskDto addItem(
            UUID id,
            String text
    ) {
        return taskMapper.toDto(taskService.addItem(id, text, CurrentUser.login()));
    }

    @Override
    @Transactional
    public TaskDto toggleItem(UUID itemId) {
        return taskMapper.toDto(taskService.toggleItem(itemId, CurrentUser.login()));
    }

    @Override
    @Transactional
    public TaskDto removeItem(UUID itemId) {
        return taskMapper.toDto(taskService.removeItem(itemId, CurrentUser.login()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssigneeOption> assignees() {
        return users.activeUsers().stream()
                .map(taskMapper::toAssignee)
                .sorted(Comparator.comparing(AssigneeOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
