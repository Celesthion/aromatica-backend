package com.aromatica.modules.workspace.task.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.workspace.task.model.TaskItem;

import java.util.UUID;

public interface TaskItemRepository extends EntityRepository<TaskItem, UUID> {
}
