package com.aromatica.modules.pos.draft.model;

import com.aromatica.common.audit.CreatedAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Отложенный чек. payload — снапшот корзины, бэк его не разбирает; sumMinor, positions
 * и preview нужны для списка.
 */
@Entity
@Table(name = "pos_draft")
@Getter
@Setter
@NoArgsConstructor
public class PosDraft extends CreatedAudit {

    @Id
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "sum_minor", nullable = false)
    private long sumMinor;

    @Column(nullable = false)
    private int positions;

    /** Первые 2–3 названия товаров, подпись в списке отложенных. */
    @Column
    private String preview;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
}
