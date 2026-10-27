package com.aromatica.modules.workspace.task.service;

import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.search.TaskSearchRequest;
import org.springframework.data.domain.Page;

/** Задачи страницами: админ видит все, сотрудник — назначенные ему и созданные им. */
public interface TaskSearchService {

    Page<Task> search(String login, TaskSearchRequest request);
}
