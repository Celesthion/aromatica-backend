package com.aromatica.modules.masterdata.catalog.giftrule.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Условие акции; заполнены только поля своего типа: SUM → {@code minSumMinor},
 * KIND → {@code kind} (+ {@code minVolumeMl}), PRODUCT → {@code productId}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class GiftRuleCondition {

    public enum Type { SUM, KIND, PRODUCT }

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    /** Номер группы «И»; между группами — «ИЛИ». Нумерация с нуля, без дыр. */
    @Column(name = "group_no", nullable = false)
    private int groupNo;

    @Column(name = "min_sum_minor")
    private Long minSumMinor;

    @Column(name = "kind")
    private String kind;

    @Column(name = "min_volume_ml", precision = 14, scale = 3)
    private BigDecimal minVolumeMl;

    @Column(name = "product_id")
    private UUID productId;
}
