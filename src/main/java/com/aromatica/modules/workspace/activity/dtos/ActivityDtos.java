package com.aromatica.modules.workspace.activity.dtos;

import java.time.LocalDateTime;

public final class ActivityDtos {

    private ActivityDtos() {}

    /** kind — иконка (doc/shift/task). */
    public record ActivityItem(
            String kind,
            String section,
            String action,
            String title,
            LocalDateTime at
    ) {}
}
