package com.aromatica.modules.pos.sale.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.reservation.service.ReservationService.Availability;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Склад в чеке: откуда строки и хватает ли товара (доступно = остаток − брони других).
 * Касается всех строк, включая подарки и дописанную сервером воду.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReceiptStockGuard {

    ReservationService reservationService;
    ProductService productService;

    /**
     * Только склад смены или его полка — иначе можно продать чужой склад. Готовое есть только
     * на полке; комплект на складе виртуален и разворачивается при проведении.
     */
    public void requireLineWarehouses(
            UUID shiftWarehouse,
            UUID shelf,
            List<ReceiptLine> lines,
            ReceiptProducts products
    ) {
        for (ReceiptLine l : lines) {
            UUID w = l.warehouseId();
            if (w != null && !w.equals(shiftWarehouse) && !w.equals(shelf)) {
                throw new BusinessRuleException("Строка чека может быть только со склада смены или его полки");
            }
        }
        for (ReceiptLine l : lines) {
            CatalogProduct p = products.get(l.productId());
            if (p != null && p.hasKind(CatalogProduct.Kind.READY) && !shelf.equals(l.warehouseId())) {
                throw new BusinessRuleException("«" + p.getName() + "» продаётся только с полки");
            }
        }
    }

    /** Ранняя проверка, без блокировок: понятный отказ до того, как документ заведётся. */
    public void requireStockForSale(
            UUID shiftWarehouse,
            UUID shelf,
            List<ReceiptLine> lines,
            ReceiptProducts products,
            @Nullable UUID sellingReservation
    ) {
        for (var byWarehouse : neededByWarehouse(shiftWarehouse, shelf, lines, products).entrySet()) {
            requireStockOn(byWarehouse.getKey(), byWarehouse.getValue(), sellingReservation);
        }
    }

    /**
     * Повтор под блокировками проведения: 409, если параллельный чек успел забрать остаток.
     * Ранняя проверка шла без блокировок и от этого не спасает.
     */
    public void requireStockAfterSale(
            UUID shiftWarehouse,
            UUID shelf,
            List<ReceiptLine> lines,
            ReceiptProducts products,
            @Nullable UUID sellingReservation
    ) {
        for (var byWarehouse : neededByWarehouse(shiftWarehouse, shelf, lines, products).entrySet()) {
            reservationService.availability(byWarehouse.getKey(), byWarehouse.getValue().keySet(), sellingReservation)
                    .forEach((productId, a) -> {
                        if (a.available().signum() < 0) {
                            throw new ConflictException("«" + productService.nameOf(productId)
                                    + "» только что забрали в другом чеке — остатка не хватает");
                        }
                    });
        }
    }

    /**
     * Что чек заберёт с каждого склада; комплект на складе смены — составляющими.
     */
    private static Map<UUID, Map<UUID, BigDecimal>> neededByWarehouse(
            UUID shiftWarehouse,
            UUID shelf,
            List<ReceiptLine> lines,
            ReceiptProducts products
    ) {
        Map<UUID, Map<UUID, BigDecimal>> needed = new HashMap<>();
        for (ReceiptLine l : lines) {
            UUID w = l.warehouseId() != null ? l.warehouseId() : shiftWarehouse;
            Map<UUID, BigDecimal> onWarehouse = needed.computeIfAbsent(w, k -> new HashMap<>());
            if (products.hasKind(l.productId(), CatalogProduct.Kind.KIT) && !w.equals(shelf)) {
                for (ProductComponent c : products.componentsOf(l.productId())) {
                    onWarehouse.merge(c.getComponentId(), c.getQty().multiply(l.qty()), BigDecimal::add);
                }
            } else {
                onWarehouse.merge(l.productId(), l.qty(), BigDecimal::add);
            }
        }
        return needed;
    }

    private void requireStockOn(
            UUID warehouseId,
            Map<UUID, BigDecimal> needed,
            @Nullable UUID sellingReservation
    ) {
        Map<UUID, Availability> availability =
                reservationService.availability(warehouseId, needed.keySet(), sellingReservation);
        for (var e : needed.entrySet()) {
            BigDecimal reserved = availability.get(e.getKey()).reserved();
            BigDecimal onHand = availability.get(e.getKey()).onHand();
            BigDecimal have = onHand.subtract(reserved);
            if (have.compareTo(e.getValue()) >= 0) {
                continue;
            }
            String name = productService.nameOf(e.getKey());
            String need = e.getValue().stripTrailingZeros().toPlainString();
            if (reserved.signum() > 0) {
                throw new ConflictException("«" + name + "»: свободно "
                        + have.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                        + ", забронировано " + reserved.stripTrailingZeros().toPlainString()
                        + ", нужно " + need + " — снимите бронь или дождитесь её срока");
            }
            throw new ConflictException("«" + name + "»: на складе "
                    + onHand.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + ", нужно " + need + " — продажа в минус запрещена");
        }
    }
}
