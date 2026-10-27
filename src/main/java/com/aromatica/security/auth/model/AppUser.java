package com.aromatica.security.auth.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends Auditable {

    /**
     * ADMIN — полный доступ; SELLER — рядовой кассир (только касса).
     * MERCHANDISER — склад + каталог; SENIOR_SELLER — касса + возвраты/сторно + отчёты + смены;
     * ONLINE_MANAGER — каталог + продажи (без кассы/денег/кадров).
     */
    public enum Role { ADMIN, SELLER, MERCHANDISER, SENIOR_SELLER, ONLINE_MANAGER }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String login;

    /** BCrypt-хэш пароля. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "display_name")
    private String displayName;

    /** Архив: вход запрещён, в списках не показывается; история документов сохраняется. */
    private boolean archived;

    /** Склады, к которым допущен сотрудник (для продавца). ADMIN не ограничен. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_warehouse", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "warehouse_id")
    private Set<UUID> warehouseIds = new HashSet<>();

    /** Админу доступны все склады, остальным — только закреплённые за ними. */
    public boolean canAccessWarehouse(UUID warehouseId) {
        return role == Role.ADMIN || (warehouseId != null && warehouseIds.contains(warehouseId));
    }

    /** Как показывать сотрудника: отображаемое имя, а если его нет — логин. */
    public String displayNameOrLogin() {
        return displayName != null && !displayName.isBlank() ? displayName : login;
    }

    /** Логин хранится в нижнем регистре без пробелов по краям: «Ivan» и «ivan » — один логин. */
    public static String normalizeLogin(String login) {
        return login == null ? null : login.trim().toLowerCase(Locale.ROOT);
    }
}
