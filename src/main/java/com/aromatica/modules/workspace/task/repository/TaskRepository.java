package com.aromatica.modules.workspace.task.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.workspace.task.model.Task;

import java.util.UUID;

/** Только страницами: за годы задач накопится много. */
public interface TaskRepository extends BaseRepository<Task, UUID> {
}
