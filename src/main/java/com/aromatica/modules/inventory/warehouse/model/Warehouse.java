package com.aromatica.modules.inventory.warehouse.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Entity
@Table(name = "warehouse")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends Auditable {

    @Id
    private UUID id;

    private String name;

    private String address;

    @Column(name = "is_default")
    private boolean isDefault;

    private boolean archived;

    /** Склад магазина или его полка — см. docs/KITS-SHELF.md. */
    public enum Kind { STORE, SHELF }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.STORE;

    /** У полки — склад магазина, которому она принадлежит; у склада NULL. */
    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    public boolean isShelf() {
        return kind == Kind.SHELF;
    }
}
