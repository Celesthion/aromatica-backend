package com.aromatica.security.auth.service;

import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.search.UserSearchRequest;
import org.springframework.data.domain.Page;

/** Поиск сотрудников для списка: логин или имя подстрокой, роль, архивные по запросу. */
public interface UserSearchService {

    Page<AppUser> search(UserSearchRequest request);
}
