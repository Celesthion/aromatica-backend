package com.aromatica.modules.masterdata.catalog.productkind.model;

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

/**
 * Форма товара. Системные (system = true) зашиты в поведение: вскрытие, касса, цена за мл;
 * пользовательские ведут себя как обычный товар.
 */
@Entity
@Table(name = "product_kind")
@Getter
@Setter
@NoArgsConstructor
public class ProductKind extends Auditable {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    /** Единица по умолчанию для товаров этой формы. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatalogProduct.Unit unit = CatalogProduct.Unit.PCS;

    /** Поведение системной формы зашито в код, поэтому её нельзя менять и архивировать. */
    @Column(nullable = false)
    private boolean system;

    @Column(nullable = false)
    private boolean archived;

    /** Можно ли дарить товары этой формы. Флаг не связан с архивом. */
    @Column(name = "gift_eligible", nullable = false)
    private boolean giftEligible = true;

    /** Имя lucide-иконки формы для карточки кассы (null — фолбэк по коду формы). */
    @Column
    private @Nullable String icon;

    /** HEX цвета-акцента формы на карточке кассы (null — фолбэк по коду формы). */
    @Column
    private @Nullable String color;
}
