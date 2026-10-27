package com.aromatica.modules.pos.sale.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Расход парфюмерной воды по строкам разлива: клиент за неё не платит, но остаток и себестоимость
 * учитываются. Объём воды: qty × (100 − концентрация) / концентрация.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BaseConsumption {

    /** Миллилитры считаем до сотых — как и в кассе. */
    private static final int ML_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    ProductService productService;

    /**
     * Строки чека плюс вода на каждую строку разбавленного разлива. Воды нет в каталоге — отказ:
     * иначе остаток воды молча не убывал бы, а себестоимость занижалась.
     */
    public List<ReceiptLine> withBaseConsumption(
            List<ReceiptLine> lines,
            ReceiptProducts products
    ) {
        requireDilutableKinds(lines, products);
        List<ReceiptLine> withBase = new ArrayList<>(lines);
        CatalogProduct base = null;
        for (ReceiptLine l : lines) {
            BigDecimal pct = l.dilutionPct();
            if (pct == null || pct.signum() <= 0 || pct.compareTo(HUNDRED) >= 0) {
                continue;
            }
            BigDecimal water = l.qty().multiply(HUNDRED.subtract(pct)).divide(pct, ML_SCALE, RoundingMode.HALF_UP);
            if (water.signum() <= 0) {
                continue;
            }
            if (base == null) {
                base = productService.findActiveByKind(CatalogProduct.Kind.BASE.name())
                        .stream().findFirst()
                        .orElseThrow(() -> new ConflictException(
                                "Не заведена парфюмерная вода — разбавленный разлив продать нельзя. "
                                        + "Заведите её в каталоге или продайте масло без разбавления"));
                products.add(base);
            }
            // вода идёт своей строкой на каждую строку масла, с той же меткой вещи:
            // так возврат кладёт на полку флакон целиком, а не сумму воды по чеку
            withBase.add(ReceiptLine.free(base.getId(), water, l.bundleId(), l.warehouseId()));
        }
        return withBase;
    }

    /**
     * Концентрацию несёт только разлив; у штучного товара — отказ, иначе вода
     * спишется на него без следа.
     */
    private static void requireDilutableKinds(
            List<ReceiptLine> lines,
            ReceiptProducts products
    ) {
        for (ReceiptLine l : lines) {
            CatalogProduct p = products.get(l.productId());
            // если товара нет в каталоге, отказ придёт ниже, от самого документа
            if (l.dilutionPct() != null && p != null && !p.hasKind(CatalogProduct.Kind.BULK)) {
                throw new BusinessRuleException("Концентрация бывает только у разлива");
            }
        }
    }
}
