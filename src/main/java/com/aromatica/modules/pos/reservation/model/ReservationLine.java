package com.aromatica.modules.pos.reservation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reservation_line")
@Getter
@Setter
@NoArgsConstructor
public class ReservationLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Дробное: бронируют и на мл, точность как у строк документов. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    /** Цена за единицу в минорных единицах на момент создания брони. */
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "line_no", nullable = false)
    private int lineNo;
}
