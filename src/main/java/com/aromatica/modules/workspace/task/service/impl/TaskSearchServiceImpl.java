package com.aromatica.modules.workspace.task.service.impl;

import com.aromatica.modules.workspace.task.model.QTask;
import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.repository.TaskRepository;
import com.aromatica.modules.workspace.task.search.TaskSearchRequest;
import com.aromatica.modules.workspace.task.service.TaskSearchService;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskSearchServiceImpl implements TaskSearchService {

    TaskRepository taskRepository;
    UserDirectory users;

    @Override
    public Page<Task> search(
            String login,
            TaskSearchRequest request
    ) {
        AppUser me = users.require(login);
        QTask t = QTask.task;
        BooleanBuilder p = new BooleanBuilder();
        if (me.getRole() != AppUser.Role.ADMIN) {
            // сотрудник видит назначенные ему или созданные им
            p.and(t.assigneeId.eq(me.getId()).or(t.createdBy.eq(me.getId())));
        }
        if (request.getStatus() != null) {
            p.and(t.status.eq(request.getStatus()));
        }
        String q = blankToNull(request.getQ());
        if (q != null) {
            p.and(t.title.containsIgnoreCase(q));
        }
        return taskRepository.findAll(p, request);
    }
}
