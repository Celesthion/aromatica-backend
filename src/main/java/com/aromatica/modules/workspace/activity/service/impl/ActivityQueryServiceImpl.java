package com.aromatica.modules.workspace.activity.service.impl;

import com.aromatica.modules.workspace.activity.model.ActivityLog;
import com.aromatica.modules.workspace.activity.repository.ActivityLogRepository;
import com.aromatica.modules.workspace.activity.service.ActivityQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActivityQueryServiceImpl implements ActivityQueryService {

    ActivityLogRepository repository;

    @Override
    public Slice<ActivityLog> forActor(
            UUID actorId,
            String section,
            Pageable pageable
    ) {
        boolean all = section == null || section.isBlank() || section.equals("all");
        return all
                ? repository.findByActorId(actorId, pageable)
                : repository.findByActorIdAndSection(actorId, section, pageable);
    }
}
