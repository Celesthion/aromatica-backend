package com.aromatica.security.auth.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.model.UserNameRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends BaseRepository<AppUser, UUID> {

    Optional<AppUser> findByLogin(String login);

    /** Имена без загрузки сущностей (и их складов). */
    @Query("select u.id as id, u.displayName as displayName, u.login as login from AppUser u where u.id in :ids")
    List<UserNameRow> findNames(@Param("ids") Collection<UUID> ids);

    List<AppUser> findByArchivedFalse();

    /** Есть ли другой активный пользователь этой роли (инвариант «последний админ»). */
    boolean existsByRoleAndArchivedFalseAndIdNot(AppUser.Role role, UUID id);
}
