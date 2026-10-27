package com.aromatica.security.auth.service;

import com.aromatica.security.auth.model.AppUser;

import java.util.UUID;

/**
 * Нельзя убрать последнего активного админа и архивировать себя. Удаления нет, только архив —
 * у документов сохраняется автор.
 */
public interface UserService {

    /** Смена собственного пароля: текущий пароль обязателен. */
    void changeOwnPassword(String login, String current, String next);

    /** Новый сотрудник: пароль по политике, логин свободен; роль не задана — продавец. */
    AppUser create(AppUser user, String password);

    /** Профиль: имя, роль (null — не менять) и склады. Последнего админа не разжаловать. */
    AppUser update(UUID id, AppUser changes);

    /** Новый пароль от администратора; выданные сессии сотрудника гасятся. */
    AppUser resetPassword(UUID id, String password);

    /** Архив/разархив. Себя и последнего активного админа архивировать нельзя. */
    AppUser setArchived(UUID id, boolean archived, String actorLogin);
}
