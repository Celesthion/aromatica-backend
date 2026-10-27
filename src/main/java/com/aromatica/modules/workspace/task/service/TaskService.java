package com.aromatica.modules.workspace.task.service;

import com.aromatica.modules.workspace.task.model.Task;

import java.util.UUID;

/**
 * Продавец ставит задачи только себе, админ — кому угодно. Отмечать выполнение могут
 * исполнитель, автор и админ, править и удалять — автор и админ. Поиск — в {@link TaskSearchService}.
 */
public interface TaskService {

    Task create(Task task, String login);

    /** Исполнителя меняет только админ. */
    Task update(UUID id, Task changes, String login);

    void delete(UUID id, String login);

    Task setDone(UUID id, boolean done, String login);

    Task addItem(UUID id, String text, String login);

    Task toggleItem(UUID itemId, String login);

    Task removeItem(UUID itemId, String login);
}
