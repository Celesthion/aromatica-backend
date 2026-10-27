package com.aromatica.modules.workspace.activity.endpoint;

import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.workspace.activity.dtos.ActivityDtos.ActivityItem;

import java.util.UUID;

public interface ActivityEndpoint {

    SliceResponse<ActivityItem> forEmployee(UUID employeeId, String section, int page, int size);
}
