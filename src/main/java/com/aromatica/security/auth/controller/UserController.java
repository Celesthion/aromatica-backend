package com.aromatica.security.auth.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.security.auth.dtos.UserDtos.CreateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UpdateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UserDto;
import com.aromatica.security.auth.endpoint.UserEndpoint;
import com.aromatica.security.auth.search.UserSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Сотрудники", description = "Только ADMIN. Удаления нет, только архив")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserEndpoint userEndpoint;

    @Operation(
            summary = "Поиск сотрудников",
            description = "Фильтры: подстрока логина или имени (без учёта регистра), роль, показывать ли архивных (по умолчанию нет)"
    )
    @PostMapping("/search")
    public PageResponse<UserDto> search(@RequestBody UserSearchRequest request) {
        return userEndpoint.search(request);
    }

    @Operation(summary = "Создать сотрудника")
    @PostMapping
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return userEndpoint.create(request);
    }

    @Operation(summary = "Обновить сотрудника")
    @PutMapping("/{id}")
    public UserDto update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userEndpoint.update(id, request);
    }

    @Operation(
            summary = "Отправить сотрудника в архив",
            description = "Повторный вызов ничего не меняет. Себя и последнего активного админа архивировать нельзя"
    )
    @PostMapping("/{id}/archive")
    public UserDto archive(@PathVariable UUID id) {
        return userEndpoint.setArchived(id, true);
    }

    @Operation(summary = "Вернуть сотрудника из архива")
    @PostMapping("/{id}/restore")
    public UserDto restore(@PathVariable UUID id) {
        return userEndpoint.setArchived(id, false);
    }
}
