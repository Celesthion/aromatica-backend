package com.aromatica.modules.masterdata.catalog.giftrule.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Правило подарка (акция): условия {@link GiftRuleCondition} в группах — внутри «И», между «ИЛИ».
 * Подарок — набор позиций {@link GiftItem}, выдаётся целиком.
 */
@Entity
@Table(name = "gift_rule")
@Getter
@Setter
@NoArgsConstructor
public class GiftRule extends Auditable {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "gift_rule_condition", joinColumns = @JoinColumn(name = "rule_id"))
    @OrderColumn(name = "sort")
    private List<GiftRuleCondition> conditions = new ArrayList<>();

    /** +1 подарок за каждые N сом сверх порога (только вместе с SUM-условием). */
    @Column(name = "step_minor")
    private Long stepMinor;

    /** Не более N подарков на чек (null — без потолка). */
    @Column(name = "max_gifts")
    private @Nullable Integer maxGifts;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "gift_rule_item", joinColumns = @JoinColumn(name = "rule_id"))
    @OrderColumn(name = "sort")
    private List<GiftItem> items = new ArrayList<>();
}
