package com.aromatica.modules.masterdata.catalog.giftrule.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиция подарочного набора: конкретный товар или любой товар формы {@code kind},
 * его выбирают на кассе. {@code qty} — в единице формы.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class GiftItem {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "kind")
    private String kind;

    @Column(name = "qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;
}
