package com.aromatica.security.auth.mapper;

import com.aromatica.security.auth.dtos.AuthDtos.LoginResponse;
import com.aromatica.security.auth.dtos.AuthDtos.TokenPair;
import com.aromatica.security.auth.dtos.AuthDtos.UserInfo;
import com.aromatica.security.auth.dtos.UserDtos.CreateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UpdateUserRequest;
import com.aromatica.security.auth.dtos.UserDtos.UserDto;
import com.aromatica.security.auth.model.AppUser;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** AppUser → UserDto (enum role → name(), Set warehouseIds → List). */
@Mapper
public interface UserMapper {

    UserDto toDto(AppUser user);

    UserInfo toInfo(AppUser user);

    /** Ответ на вход: выданная пара токенов и кто вошёл. */
    LoginResponse toLoginResponse(String accessToken, String refreshToken, AppUser user);

    TokenPair toTokenPair(String accessToken, String refreshToken);

    /** Пароль — отдельно: в сущности только хэш, его считает сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR,
            ignoreUnmappedSourceProperties = "password")
    AppUser toEntity(CreateUserRequest request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR,
            ignoreUnmappedSourceProperties = "password")
    AppUser toEntity(UpdateUserRequest request);
}
