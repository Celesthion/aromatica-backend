package com.aromatica.modules.pos.sale.service;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Товары и состав комплектов чека, прочитанные один раз на все проверки.
 */
public record ReceiptProducts(
        Map<UUID, CatalogProduct> byId,
        Map<UUID, List<ProductComponent>> compositions
) {

    public static ReceiptProducts load(
            ProductService productService,
            Collection<ReceiptLine> lines
    ) {
        Map<UUID, CatalogProduct> byId = new HashMap<>();
        productService.findAllById(lines.stream().map(ReceiptLine::productId).distinct().toList())
                .forEach(p -> byId.put(p.getId(), p));
        List<UUID> kitIds = byId.values().stream()
                .filter(p -> p.hasKind(CatalogProduct.Kind.KIT)).map(CatalogProduct::getId).toList();
        Map<UUID, List<ProductComponent>> compositions =
                kitIds.isEmpty() ? Map.of() : productService.componentsOf(kitIds);
        return new ReceiptProducts(byId, compositions);
    }

    public @Nullable CatalogProduct get(UUID productId) {
        return byId.get(productId);
    }

    /** Товар, который сервер дописал в чек сам (парфюмерная вода). */
    public void add(CatalogProduct product) {
        byId.put(product.getId(), product);
    }

    public boolean hasKind(
            UUID productId,
            CatalogProduct.Kind kind
    ) {
        CatalogProduct p = byId.get(productId);
        return p != null && p.hasKind(kind);
    }

    public String kindOf(UUID productId) {
        CatalogProduct p = byId.get(productId);
        return p != null ? p.getKind() : "";
    }

    public List<ProductComponent> componentsOf(UUID kitId) {
        return compositions.getOrDefault(kitId, List.of());
    }
}
