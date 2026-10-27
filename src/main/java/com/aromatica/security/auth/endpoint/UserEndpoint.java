package com.aromatica.security.auth.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.security.auth.dtos.UserDtos.CreateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UpdateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UserDto;
import com.aromatica.security.auth.search.UserSearchRequest;

import java.util.UUID;

/** Сотрудники в админке: список, CRUD (архив вместо удаления), лента активности. */
public interface UserEndpoint {

    PageResponse<UserDto> search(UserSearchRequest request);

    UserDto create(CreateUserRequest request);

    UserDto update(UUID id, UpdateUserRequest request);

    UserDto setArchived(UUID id, boolean archived);

}
