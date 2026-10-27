package com.aromatica.modules.workspace.activity.endpoint.impl;

import com.aromatica.common.error.NotFoundException;
import com.aromatica.common.search.Paging;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.workspace.activity.dtos.ActivityDtos.ActivityItem;
import com.aromatica.modules.workspace.activity.endpoint.ActivityEndpoint;
import com.aromatica.modules.workspace.activity.mapper.ActivityMapper;
import com.aromatica.modules.workspace.activity.model.ActivityLog;
import com.aromatica.modules.workspace.activity.service.ActivityQueryService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActivityEndpointImpl implements ActivityEndpoint {

    ActivityQueryService activityQuery;
    ActivityMapper activityMapper;
    UserDirectory users;

    @Override
    @Transactional(readOnly = true)
    public SliceResponse<ActivityItem> forEmployee(
            UUID employeeId,
            String section,
            int page,
            int size
    ) {
        // без displayName берётся логин, так что null — пользователь не найден
        if (users.name(employeeId) == null) {
            throw new NotFoundException("Сотрудник не найден: " + employeeId);
        }
        Pageable pg = PageRequest.of(Paging.page(page), Paging.size(size),
                Sort.by(Sort.Order.desc("at"), Sort.Order.desc("id")));
        Slice<ActivityLog> found = activityQuery.forActor(employeeId, section, pg);
        return new SliceResponse<>(activityMapper.toItems(found.getContent()), found.getNumber(), found.hasNext());
    }
}
