package com.aromatica.modules.finance.gift.model;

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

/** Журнал знаковый: погашение минусом, возврат плюсом. По нему отвечаем клиенту «где мои 2000». */
@Entity
@Table(name = "gift_certificate_use")
@Getter
@Setter
@NoArgsConstructor
public class GiftCertificateUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_id", nullable = false)
    private UUID certificateId;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private LocalDateTime at = LocalDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;
}
