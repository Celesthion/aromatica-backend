package com.aromatica.modules.inventory.doc.posting;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Данные проведения сверх строк: товары, состав, полки, заранее заблокированные балансы и копящиеся движения. */
public record PostingContext(
        Map<UUID, CatalogProduct> products,
        Map<UUID, List<ProductComponent>> compositions,
        Set<UUID> shelves,
        Map<BalanceKey, StockBalance> balances,
        List<StockMovement> movements
) {

    /** Не нашёлся — стратегия забыла ключ в collectBalanceKeys, это ошибка кода. */
    public StockBalance balance(
            UUID productId,
            UUID warehouseId
    ) {
        StockBalance b = balances.get(new BalanceKey(productId, warehouseId));
        if (b == null) {
            throw new IllegalStateException("Баланс не заблокирован заранее: " + productId + " @ " + warehouseId);
        }
        return b;
    }

    public boolean isComposite(UUID productId) {
        CatalogProduct p = products.get(productId);
        return p != null && p.isComposite();
    }

    public boolean isKit(UUID productId) {
        CatalogProduct p = products.get(productId);
        return p != null && p.hasKind(CatalogProduct.Kind.KIT);
    }

    public boolean isShelf(UUID warehouseId) {
        return shelves.contains(warehouseId);
    }

    public List<ProductComponent> componentsOf(UUID productId) {
        return compositions.getOrDefault(productId, List.of());
    }

    public String productName(UUID productId) {
        CatalogProduct p = products.get(productId);
        return p != null ? p.getName() : productId.toString();
    }

    public static UUID lineWarehouse(
            StockDoc doc,
            StockDocLine line
    ) {
        return line.getWarehouseId() != null ? line.getWarehouseId() : doc.getWarehouseId();
    }

    public static long perUnit(
            long totalCost,
            BigDecimal qty
    ) {
        return qty.signum() == 0 ? 0
                : BigDecimal.valueOf(totalCost).divide(qty, 0, RoundingMode.HALF_UP).longValueExact();
    }

    public void writeMovement(
            StockDoc doc,
            StockDocLine line,
            BigDecimal qtyDelta,
            long costMinor,
            UUID warehouseId
    ) {
        writeMovement(doc, line, line.getProductId(), qtyDelta, costMinor, warehouseId);
    }

    /** Движение по товару, отличному от товара строки, — составляющие комплекта. */
    public void writeMovement(
            StockDoc doc,
            StockDocLine line,
            UUID productId,
            BigDecimal qtyDelta,
            long costMinor,
            UUID warehouseId
    ) {
        StockMovement m = new StockMovement();
        m.setDocId(doc.getId());
        m.setLineId(line.getId());
        m.setProductId(productId);
        m.setWarehouseId(warehouseId);
        m.setQtyDelta(qtyDelta);
        m.setCostMinor(costMinor);
        m.setMoment(doc.getMoment());
        movements.add(m);
    }
}
