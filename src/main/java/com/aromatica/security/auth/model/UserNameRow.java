package com.aromatica.security.auth.model;

import java.util.UUID;

/** Имя сотрудника без загрузки сущности (и его складов). */
public interface UserNameRow {
    UUID getId();
    String getDisplayName();
    String getLogin();
}
