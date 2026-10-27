package com.aromatica.security.auth.endpoint.impl;

import com.aromatica.common.error.NotFoundException;
import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.dtos.UserDtos.CreateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UpdateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UserDto;
import com.aromatica.security.auth.endpoint.UserEndpoint;
import com.aromatica.security.auth.mapper.UserMapper;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.search.UserSearchRequest;
import com.aromatica.security.auth.service.UserDirectory;
import com.aromatica.security.auth.service.UserSearchService;
import com.aromatica.security.auth.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserEndpointImpl implements UserEndpoint {

    UserService userService;
    UserSearchService searchService;
    UserMapper mapper;
    UserDirectory users;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(UserSearchRequest request) {
        Page<AppUser> page = searchService.search(request);
        return pageMapper.toResponse(page, mapper::toDto);
    }

    @Override
    @Transactional
    public UserDto create(CreateUserRequest request) {
        return mapper.toDto(userService.create(mapper.toEntity(request), request.password()));
    }

    /** Под одним PUT два действия карточки: правка профиля и новый пароль — что пришло, то и делаем. */
    @Override
    @Transactional
    public UserDto update(
            UUID id,
            UpdateUserRequest request
    ) {
        AppUser user = null;
        if (request.displayName() != null || request.role() != null || request.warehouseIds() != null) {
            user = userService.update(id, mapper.toEntity(request));
        }
        if (request.password() != null && !request.password().isBlank()) {
            user = userService.resetPassword(id, request.password());
        }
        return mapper.toDto(user != null ? user : users.byId(id)
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден: " + id)));
    }

    @Override
    @Transactional
    public UserDto setArchived(
            UUID id,
            boolean archived
    ) {
        return mapper.toDto(userService.setArchived(id, archived, CurrentUser.login()));
    }
}
