package com.aromatica.modules.pos.sale.model;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Строка чека, возврата или «на полку». Скидка уже суммой в тыйынах.
 *
 * @param giftRuleId  акция, по которой строка подарочная; проверяет ReceiptGiftGuard
 * @param giftGroupId набор подарков одной акции, выданный целиком
 * @param dilutionPct доля масла в разливе, 30…100; null — строка не про разлив
 * @param bundleId    метка строк одной вещи: масло, вода и атомайзер одного флакона
 * @param warehouseId склад строки, если не склад смены (полка)
 */
public record ReceiptLine(

        UUID productId,

        BigDecimal qty,

        long priceMinor,

        long discountMinor,

        boolean gift,

        @Nullable
        UUID giftRuleId,

        @Nullable
        String giftGroupId,

        @Nullable
        BigDecimal dilutionPct,

        @Nullable
        UUID bundleId,

        @Nullable
        UUID warehouseId
) {

    /** Строка, за которую клиент не платит: вода разлива, части вещи для полки. */
    public static ReceiptLine free(
            UUID productId,
            BigDecimal qty,
            @Nullable UUID bundleId,
            @Nullable UUID warehouseId
    ) {
        return new ReceiptLine(productId, qty, 0, 0, false, null, null, null, bundleId, warehouseId);
    }

    public long sumMinor() {
        return CostMath.lineSumMinor(qty, priceMinor, discountMinor);
    }

    /** Та же строка на другом складе (полка) — для возврата и продажи с полки. */
    public ReceiptLine onWarehouse(UUID warehouse) {
        return new ReceiptLine(productId, qty, priceMinor, discountMinor, gift,
                giftRuleId, giftGroupId, dilutionPct, bundleId, warehouse);
    }

    /** Строка документа; номер и процент скидки проставит DocService. */
    public StockDocLine toDocLine() {
        StockDocLine line = StockDocLine.of(productId, qty, priceMinor, discountMinor);
        line.setGift(gift);
        line.setDilutionPct(dilutionPct);
        line.setBundleId(bundleId);
        line.setWarehouseId(warehouseId);
        return line;
    }
}
