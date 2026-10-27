package com.aromatica.modules.masterdata.catalog.product.model;

import com.aromatica.common.audit.Auditable;
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

import java.math.BigDecimal;
import java.util.UUID;

/** Товар каталога (SKU). Деньги в копейках. */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class CatalogProduct extends Auditable {

    @Id
    private UUID id;

    /** Полное имя для показа. У новых товаров собирается из частей (ProductNaming.compose). */
    private String name;

    /** Отличие варианта, например цвет. Необязательно. */
    private String label;

    private String article;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_minor")
    private Long priceMinor;

    private boolean archived;

    /** Системные формы, на которые завязано поведение; пользовательских форм из product_kind здесь нет. */
    public enum Kind { BOTTLE, TESTER, SPLIT, BULK, BASE, GOODS, PACKAGING, ATOMIZER, KIT, READY }

    /** Товар с составом: комплект заводит администратор, готовое — система. */
    public boolean isComposite() {
        return hasKind(Kind.KIT) || hasKind(Kind.READY);
    }

    /** Ключ состава готового: одинаковый состав — одна карточка, «2 шт», а не два предмета. Не у READY — NULL. */
    @Column(name = "composition_key")
    private @Nullable String compositionKey;

    public enum Unit { PCS, ML }

    /** Аромат-родитель; NULL для аксессуаров и прочих товаров. */
    @Column(name = "model_id")
    private UUID modelId;

    /** Код формы из справочника product_kind; у системных совпадает с Kind.name(). */
    @Column(nullable = false)
    private String kind = Kind.GOODS.name();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit = Unit.PCS;

    /** Объём флакона или тестера в мл: столько даёт вскрытие одной штуки. */
    @Column(name = "volume_ml", precision = 14, scale = 3)
    private BigDecimal volumeMl;

    /** Пользовательские формы не совпадают ни с одной системной. */
    public boolean hasKind(Kind k) {
        return k.name().equals(kind);
    }
}
