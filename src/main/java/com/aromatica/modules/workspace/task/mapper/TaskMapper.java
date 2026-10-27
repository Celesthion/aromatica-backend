package com.aromatica.modules.workspace.task.mapper;

import com.aromatica.modules.workspace.task.dtos.TaskDtos.AssigneeOption;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.CreateTaskRequest;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskDto;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.TaskItemDto;
import com.aromatica.modules.workspace.task.dtos.TaskDtos.UpdateTaskRequest;
import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.model.TaskItem;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Имена исполнителя и автора лежат в auth: грузим пачкой на весь список. */
@Mapper
public abstract class TaskMapper {

    // абстрактный маппер MapStruct создаёт сам, поэтому внедрение — полем
    @Autowired
    protected UserDirectory users;

    public TaskDto toDto(Task task) {
        return toDtos(List.of(task)).getFirst();
    }

    public List<TaskDto> toDtos(List<Task> tasks) {
        Map<UUID, String> names = users.names(tasks.stream()
                .flatMap(task -> Stream.of(task.getAssigneeId(), task.getCreatedBy())).toList());
        return tasks.stream().map(task -> toDto(task, names)).toList();
    }

    @Mapping(target = "assigneeName", expression = "java(nameOf(task.getAssigneeId(), names))")
    @Mapping(target = "createdByName", expression = "java(nameOf(task.getCreatedBy(), names))")
    protected abstract TaskDto toDto(Task task, @Context Map<UUID, String> names);

    protected abstract TaskItemDto toItem(TaskItem item);

    @Mapping(target = "name", expression = "java(user.displayNameOrLogin())")
    public abstract AssigneeOption toAssignee(AppUser user);

    /** Только то, что задаёт пользователь; пункты — текстом, нумерует и чистит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    public abstract Task toEntity(CreateTaskRequest request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    public abstract Task toEntity(UpdateTaskRequest request);

    protected TaskItem toTaskItem(String text) {
        TaskItem item = new TaskItem();
        item.setText(text);
        return item;
    }

    protected static String nameOf(
            UUID id,
            Map<UUID, String> names
    ) {
        return id == null ? null : names.get(id);
    }
}
