package com.aromatica.modules.masterdata.catalog.productmodel.model;

import com.aromatica.common.audit.Auditable;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Продукт — витринная карточка (бренд + название), например «Creed Aventus».
 * Остатков у него нет: учёт ведётся по товарам-формам (CatalogProduct с modelId).
 */
@Entity
@Table(name = "product_model")
@Getter
@Setter
@NoArgsConstructor
public class ProductModel extends Auditable {

    /** Пол для парфюмерии; null — не указан. */
    public enum Gender { MALE, FEMALE, UNISEX }

    @Id
    private UUID id;

    /** null — служебный продукт без бренда, вроде упаковки. */
    private @Nullable String brand;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private boolean archived;
}
