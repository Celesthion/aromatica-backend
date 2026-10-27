package com.aromatica.security.auth.dtos;

import com.aromatica.security.auth.model.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTO сотрудников. */
public final class UserDtos {

    private UserDtos() {}

    public record UserDto(
            UUID id,
            String login,
            String displayName,
            AppUser.Role role,
            boolean archived,
            LocalDateTime createdAt,
            List<UUID> warehouseIds
    ) {}

    public record CreateUserRequest(

            @NotBlank(message = "Не указан логин")
            @Size(max = 128)
            String login,

            String password,

            @Size(max = 255)
            String displayName,

            /** null — продавец. */
            AppUser.Role role,

            List<UUID> warehouseIds
    ) {}

    public record UpdateUserRequest(

            @Size(max = 255)
            String displayName,

            AppUser.Role role,

            String password,

            List<UUID> warehouseIds
    ) {}
}
