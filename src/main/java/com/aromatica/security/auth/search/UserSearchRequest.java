package com.aromatica.security.auth.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.security.auth.model.AppUser;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

/** Поиск сотрудников: подстрока логина/имени, роль, архивные. */
@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSearchRequest extends BaseSearchRequest<UserSort> {

    /** Подстрока по логину или отображаемому имени. */
    String q;
    /** Пусто — все роли. */
    AppUser.Role role;
    Boolean includeArchived;

    @Override
    protected UserSort getDefaultSortBy() {
        return UserSort.LOGIN;
    }
}
