package com.aromatica.modules.workspace.task.service.impl;

import com.aromatica.modules.workspace.task.event.TaskCreatedEvent;
import com.aromatica.modules.workspace.task.event.TaskDoneEvent;
import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.model.TaskItem;
import com.aromatica.modules.workspace.task.repository.TaskItemRepository;
import com.aromatica.modules.workspace.task.repository.TaskRepository;
import com.aromatica.modules.workspace.task.service.TaskService;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Правила доступа к задачам описаны в {@link TaskService}. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskServiceImpl implements TaskService {

    TaskRepository taskRepository;
    TaskItemRepository itemRepository;
    UserDirectory users;
    ApplicationEventPublisher events;
    Clock clock;

    @Transactional
    @Override
    public Task create(
            Task task,
            String login
    ) {
        AppUser me = user(login);
        task.setId(UUID.randomUUID());
        task.setTitle(task.getTitle().trim());
        // продавец ставит задачу только себе, админ кому угодно или без исполнителя
        if (!isAdmin(me)) {
            task.setAssigneeId(me.getId());
        }
        // пустые пункты отбрасываем, остальные нумеруем подряд
        List<TaskItem> texts = task.getItems() != null ? List.copyOf(task.getItems()) : List.of();
        task.setItems(new ArrayList<>());
        int pos = 0;
        for (TaskItem draft : texts) {
            if (draft.getText() != null && !draft.getText().isBlank()) {
                task.getItems().add(newItem(task, draft.getText().trim(), pos++));
            }
        }
        Task saved = taskRepository.save(task);
        events.publishEvent(new TaskCreatedEvent(saved));
        return saved;
    }

    @Transactional
    @Override
    public Task update(
            UUID id,
            Task changes,
            String login
    ) {
        Task task = editable(id, login);
        if (changes.getTitle() != null && !changes.getTitle().isBlank()) {
            task.setTitle(changes.getTitle().trim());
        }
        task.setDescription(changes.getDescription());
        if (isAdmin(user(login))) {
            task.setAssigneeId(changes.getAssigneeId());
        }
        task.setDueDate(changes.getDueDate());
        return task;
    }

    @Transactional
    @Override
    public void delete(
            UUID id,
            String login
    ) {
        taskRepository.delete(editable(id, login));
    }

    @Transactional
    @Override
    public Task setDone(
            UUID id,
            boolean done,
            String login
    ) {
        Task task = workable(id, login);
        // повторное «выполнено» ничего не меняет: ни время выполнения, ни запись в ленте
        if (task.markDone(done, LocalDateTime.now(clock)) && done) {
            events.publishEvent(new TaskDoneEvent(task, user(login).getId()));
        }
        return task;
    }

    @Transactional
    @Override
    public Task addItem(
            UUID id,
            String text,
            String login
    ) {
        Task task = workable(id, login);
        int pos = task.getItems().stream().mapToInt(TaskItem::getPosition).max().orElse(-1) + 1;
        task.getItems().add(newItem(task, text.trim(), pos));
        return task;
    }

    @Transactional
    @Override
    public Task toggleItem(
            UUID itemId,
            String login
    ) {
        TaskItem item = itemRepository.require(itemId, "Пункт не найден");
        workable(item.getTask().getId(), login);
        item.setDone(!item.isDone());
        return item.getTask();
    }

    @Transactional
    @Override
    public Task removeItem(
            UUID itemId,
            String login
    ) {
        TaskItem item = itemRepository.require(itemId, "Пункт не найден");
        Task task = editable(item.getTask().getId(), login);
        task.getItems().remove(item);
        return task;
    }

    private Task workable(
            UUID id,
            String login
    ) {
        Task task = getTask(id);
        AppUser me = user(login);
        boolean mine = me.getId().equals(task.getCreatedBy())
                || (task.getAssigneeId() != null && task.getAssigneeId().equals(me.getId()));
        if (!isAdmin(me) && !mine) {
            throw new AccessDeniedException("Нет доступа к этой задаче");
        }
        return task;
    }

    private Task editable(
            UUID id,
            String login
    ) {
        Task task = getTask(id);
        AppUser me = user(login);
        if (!isAdmin(me) && !me.getId().equals(task.getCreatedBy())) {
            throw new AccessDeniedException("Менять задачу может только автор или администратор");
        }
        return task;
    }

    private Task getTask(UUID id) {
        return taskRepository.require(id, "Задача не найдена");
    }

    private TaskItem newItem(
            Task task,
            String text,
            int position
    ) {
        TaskItem item = new TaskItem();
        item.setId(UUID.randomUUID());
        item.setTask(task);
        item.setText(text);
        item.setPosition(position);
        return item;
    }

    private AppUser user(String login) {
        return users.require(login);
    }

    private static boolean isAdmin(AppUser u) {
        return u.getRole() == AppUser.Role.ADMIN;
    }

}
