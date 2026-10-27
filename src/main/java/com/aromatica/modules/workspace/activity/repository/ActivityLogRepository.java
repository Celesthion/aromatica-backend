package com.aromatica.modules.workspace.activity.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.workspace.activity.model.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface ActivityLogRepository extends EntityRepository<ActivityLog, Long> {

    Slice<ActivityLog> findByActorId(UUID actorId, Pageable pageable);

    Slice<ActivityLog> findByActorIdAndSection(UUID actorId, String section, Pageable pageable);
}
