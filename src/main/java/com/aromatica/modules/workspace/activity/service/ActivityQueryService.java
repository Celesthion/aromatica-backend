package com.aromatica.modules.workspace.activity.service;

import com.aromatica.modules.workspace.activity.model.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

/** Чтение ленты для страниц активности сотрудника в других модулях. */
public interface ActivityQueryService {

    Slice<ActivityLog> forActor(UUID actorId, String section, Pageable pageable);
}
