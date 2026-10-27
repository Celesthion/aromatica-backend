package com.aromatica.modules.pos.pricing.service;

import com.aromatica.common.util.CostMath;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import com.aromatica.security.auth.authentication.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Потолок скидки продавца: цена строки приходит с клиента, поэтому не ниже каталожной на N%
 * (pos.max_seller_discount_pct). Админа не ограничивает; общий для чека и брони.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SellerDiscountCap {

    SettingService settingService;
    ProductService productService;

    /** Строка с ценой и скидкой; подарки сюда не передают — они бесплатны по правилу акции. */
    public record PricedLine(
            UUID productId,
            BigDecimal qty,
            long priceMinor,
            long discountMinor
    ) {}

    /**
     * heldPrices — цены брони: по ним продать можно, даже если каталог с тех пор подорожал.
     */
    public void require(
            List<PricedLine> lines,
            Map<UUID, Long> heldPrices
    ) {
        if (CurrentUser.isAdmin() || lines.isEmpty()) {
            return;
        }
        int capPct = settingService.getInt(SettingService.MAX_SELLER_DISCOUNT_PCT, 20);
        Map<UUID, Long> catalogPrice = productService.findAllById(
                        lines.stream().map(PricedLine::productId).distinct().toList()).stream()
                .filter(p -> p.getPriceMinor() != null && p.getPriceMinor() > 0)
                .collect(Collectors.toMap(CatalogProduct::getId,
                        CatalogProduct::getPriceMinor, (a, b) -> a));
        for (PricedLine line : lines) {
            BigDecimal floor = floor(catalogPrice.get(line.productId()),
                    heldPrices.get(line.productId()), capPct);
            if (floor == null) {
                continue; // нет ни каталожной, ни обещанной цены — сравнивать не с чем
            }
            // × 100 вместо деления, чтобы не терять тыйыны
            BigDecimal effective = BigDecimal.valueOf(
                    CostMath.unitPriceMinor(line.qty(), line.priceMinor(), line.discountMinor()))
                    .multiply(BigDecimal.valueOf(100));
            if (effective.compareTo(floor) < 0) {
                throw new AccessDeniedException("Скидка больше " + capPct
                        + "% — оформить может только администратор");
            }
        }
    }

    /** Нижняя граница цены × 100: каталог × (100 − N) либо обещанная цена, что ниже. */
    private static BigDecimal floor(
            Long catalog,
            Long held,
            int capPct
    ) {
        BigDecimal byCatalog = catalog != null
                ? BigDecimal.valueOf(catalog).multiply(BigDecimal.valueOf(100L - capPct)) : null;
        BigDecimal byHeld = held != null ? BigDecimal.valueOf(held).multiply(BigDecimal.valueOf(100)) : null;
        if (byCatalog == null) {
            return byHeld;
        }
        return byHeld == null ? byCatalog : byCatalog.min(byHeld);
    }
}
