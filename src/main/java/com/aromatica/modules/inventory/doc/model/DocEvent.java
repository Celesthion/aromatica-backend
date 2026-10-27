package com.aromatica.modules.inventory.doc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Событие документа: кто и когда провёл/распровёл/отменил/оплатил/сторнировал. */
@Entity
@Table(name = "doc_event")
@Getter
@Setter
@NoArgsConstructor
public class DocEvent {

    public enum Action { POST, UNPOST, CANCEL, VOID, PAY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    /** Кто выполнил действие (id пользователя). */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private LocalDateTime at = LocalDateTime.now();
}
