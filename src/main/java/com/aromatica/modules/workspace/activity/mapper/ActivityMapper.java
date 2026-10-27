package com.aromatica.modules.workspace.activity.mapper;

import com.aromatica.modules.workspace.activity.dtos.ActivityDtos.ActivityItem;
import com.aromatica.modules.workspace.activity.model.ActivityLog;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface ActivityMapper {

    ActivityItem toItem(ActivityLog log);

    List<ActivityItem> toItems(List<ActivityLog> logs);
}
