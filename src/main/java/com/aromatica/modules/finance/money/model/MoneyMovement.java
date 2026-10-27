package com.aromatica.modules.finance.money.model;

import com.aromatica.common.audit.CreatedAudit;
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

/**
 * Плюс — приход, минус — расход. Автодвижения (docId) живут вместе с проведением документа,
 * у ручных docId = null.
 */
@Entity
@Table(name = "money_movement")
@Getter
@Setter
@NoArgsConstructor
public class MoneyMovement extends CreatedAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "doc_id")
    private UUID docId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private LocalDateTime moment;

    @Column(columnDefinition = "text")
    private String comment;

    public static MoneyMovement manual(
            UUID accountId,
            long amountMinor,
            LocalDateTime moment,
            String comment
    ) {
        MoneyMovement m = new MoneyMovement();
        m.setAccountId(accountId);
        m.setAmountMinor(amountMinor);
        m.setMoment(moment);
        m.setComment(comment);
        return m;
    }

    public static MoneyMovement ofDoc(
            UUID docId,
            UUID accountId,
            long amountMinor,
            LocalDateTime moment,
            String comment
    ) {
        MoneyMovement m = manual(accountId, amountMinor, moment, comment);
        m.setDocId(docId);
        return m;
    }
}
