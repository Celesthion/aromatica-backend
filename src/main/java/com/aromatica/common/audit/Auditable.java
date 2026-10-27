package com.aromatica.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Аудит для изменяемых сущностей: к полям создания добавляет, кто и когда менял запись последним.
 * Сущностям, которые пишутся один раз, хватит {@link CreatedAudit}.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Auditable extends CreatedAudit {

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Оптимистичная блокировка: вторая из одновременных правок получит 409. Объектный тип: при null
     * Spring Data считает запись новой и делает INSERT без лишнего SELECT, хотя id задаём сами.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
