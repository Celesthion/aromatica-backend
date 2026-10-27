package com.aromatica.modules.workspace.activity.controller;

import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.workspace.activity.dtos.ActivityDtos.ActivityItem;
import com.aromatica.modules.workspace.activity.endpoint.ActivityEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Лента активности", description = "Только ADMIN. Что делал сотрудник: касса, склад, деньги, задачи")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActivityController {

    ActivityEndpoint activityEndpoint;

    @Operation(
            summary = "Лента активности сотрудника",
            description = "Страница ленты по разделу. section: pos/warehouse/money/task; пусто или all — все разделы"
    )
    @GetMapping("/{id}/activity")
    public SliceResponse<ActivityItem> activity(
            @PathVariable UUID id,
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return activityEndpoint.forEmployee(id, section, page, size);
    }
}
