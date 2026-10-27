package com.aromatica.modules.inventory.doc.model;

import com.aromatica.common.util.CostMath;
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
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_doc_line")
@Getter
@Setter
@NoArgsConstructor
public class StockDocLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id")
    private StockDoc doc;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Количество, дробное (продажа по мл). Для INVENTORY — фактический остаток. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal qty;

    /** Цена за единицу в минорных единицах (для SUPPLY — закупочная). */
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    /** Скидка строки суммой в тыйынах, по ней считаются суммы. */
    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    /** Доля масла в разливе, %: без неё объём унесённого спрея по чеку не восстановить. NULL — не разлив. */
    @Column(name = "dilution_pct")
    private @Nullable BigDecimal dilutionPct;

    /** Справочный процент по discountMinor (2 знака), пересчитывается при записи. */
    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    /** Подарок: цена 0, но себестоимость списывается (виден промо-расход). */
    @Column(name = "is_gift", nullable = false)
    private boolean gift;

    /** Себестоимость единицы, зафиксированная при проведении. */
    @Column(name = "cost_minor")
    private Long costMinor;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** Метка строк одной вещи (масло, вода, атомайзер): возврат кладёт на полку вещь целиком, а не миллилитры. */
    @Column(name = "bundle_id")
    private @Nullable UUID bundleId;

    /** Свой склад строки: один чек продаёт и с витрины, и с полки. NULL — склад документа. */
    @Column(name = "warehouse_id")
    private @Nullable UUID warehouseId;

    /** Новая строка для документа-черновика; номер, id и процент скидки проставит DocService. */
    public static StockDocLine of(
            UUID productId,
            BigDecimal qty,
            long priceMinor,
            long discountMinor
    ) {
        StockDocLine line = new StockDocLine();
        line.setProductId(productId);
        line.setQty(qty);
        line.setPriceMinor(priceMinor);
        line.setDiscountMinor(discountMinor);
        return line;
    }

    /** До проведения себестоимости нет — ноль. */
    public long costSumMinor() {
        return costMinor != null ? CostMath.costSumMinor(qty, costMinor) : 0;
    }

    /** Сумма строки со скидкой; зеркалится в SQL StockDocRepository.salesGroups — менять синхронно. */
    public long sumMinor() {
        return CostMath.lineSumMinor(qty, priceMinor, discountMinor);
    }
}
