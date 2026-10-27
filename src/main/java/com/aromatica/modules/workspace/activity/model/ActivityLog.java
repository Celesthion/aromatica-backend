package com.aromatica.modules.workspace.activity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Одно действие сотрудника; title — снимок на момент действия, не обновляется. */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime at = LocalDateTime.now();

    @Column(name = "actor_id")
    private UUID actorId;

    /** Раздел: pos/warehouse/money/task. */
    @Column(nullable = false)
    private String section;

    /** Тип для иконки: doc/shift/task. */
    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String action;

    /** stock_doc/pos_shift/task. Ссылка мягкая, без FK. */
    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false)
    private String title;

    public ActivityLog(
            LocalDateTime at,
            UUID actorId,
            String section,
            String kind,
            String action,
            String entityType,
            UUID entityId,
            String title
    ) {
        this.at = at;
        this.actorId = actorId;
        this.section = section;
        this.kind = kind;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.title = title;
    }
}
