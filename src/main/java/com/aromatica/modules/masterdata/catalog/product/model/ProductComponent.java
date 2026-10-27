package com.aromatica.modules.masterdata.catalog.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Строка состава комплекта или готового. Вложенности нет: у составляющей не может
 * быть своего состава. См. docs/KITS-SHELF.md.
 */
@Entity
@Table(name = "product_component")
@Getter
@Setter
@NoArgsConstructor
public class ProductComponent {

    @Id
    private UUID id;

    /** Товар с составом (KIT или READY). */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    /** Сколько составляющей уходит на одну штуку товара; дробное — для миллилитров. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    @Column(name = "line_no", nullable = false)
    private int lineNo;
}
