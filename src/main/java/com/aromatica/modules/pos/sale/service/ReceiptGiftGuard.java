package com.aromatica.modules.pos.sale.service;

import com.aromatica.common.error.ConflictException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.pos.pricing.service.PosGiftValidator.PaidLine;
import com.aromatica.modules.pos.pricing.service.PosGiftValidator;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Подарки должны быть заработаны платными строками — серверная копия проверки из gifts.ts.
 * Базу порогов присылает клиент, поэтому её сверяем с платными строками.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReceiptGiftGuard {

    GiftRuleService giftRuleService;

    public void requireGiftsEarned(
            List<ReceiptLine> lines,
            Long claimedBase,
            ReceiptProducts products
    ) {
        List<ReceiptLine> gifts = lines.stream().filter(ReceiptLine::gift).toList();
        if (gifts.isEmpty()) {
            return;
        }
        List<ReceiptLine> paidLines = lines.stream().filter(l -> !l.gift()).toList();
        long base = thresholdBase(paidLines, claimedBase);
        List<PaidLine> paid = paidLines.stream().map(l -> paidLine(l, products)).toList();
        Map<UUID, GiftRule> active = giftRuleService.activeRules().stream()
                .collect(Collectors.toMap(GiftRule::getId, r -> r, (a, b) -> a));

        claimsByRule(gifts, active, products).forEach((ruleId, claim) -> {
            GiftRule rule = active.get(ruleId);
            requireWithinEarned(rule, claim, PosGiftValidator.earnedGiftCount(rule, paid, base), products);
        });
    }

    /**
     * База порогов — платная сумма до скидки чека. Клиент считает её сам, поэтому
     * ограничиваем диапазоном [после всех скидок … без скидок вовсе].
     */
    private static long thresholdBase(
            List<ReceiptLine> paidLines,
            Long claimedBase
    ) {
        long afterDiscount = paidLines.stream()
                .mapToLong(l -> CostMath.lineSumMinor(l.qty(), l.priceMinor(), l.discountMinor())).sum();
        long undiscounted = paidLines.stream()
                .mapToLong(l -> CostMath.grossMinor(l.qty(), l.priceMinor())).sum();
        return claimedBase != null
                ? Math.max(afterDiscount, Math.min(claimedBase, undiscounted))
                : afterDiscount;
    }

    private static PaidLine paidLine(
            ReceiptLine line,
            ReceiptProducts products
    ) {
        CatalogProduct p = products.get(line.productId());
        boolean isMl = p != null && p.getUnit() == CatalogProduct.Unit.ML;
        return new PaidLine(line.productId(), products.kindOf(line.productId()), isMl, line.qty(),
                p != null ? p.getVolumeMl() : null);
    }

    /** Разбор подарков по правилам: набор (giftGroupId) считаем один раз, количество суммируем. */
    private static Map<UUID, Claim> claimsByRule(
            List<ReceiptLine> gifts,
            Map<UUID, GiftRule> active,
            ReceiptProducts products
    ) {
        Map<UUID, Claim> claims = new HashMap<>();
        for (ReceiptLine g : gifts) {
            requireAllowed(g, active, products);
            String groupKey = g.giftGroupId() != null ? g.giftGroupId() : g.productId().toString();
            Claim claim = claims.computeIfAbsent(g.giftRuleId(), k -> new Claim());
            claim.groups.add(groupKey);
            claim.qtyByProduct.merge(g.productId(), g.qty(), BigDecimal::add);
        }
        return claims;
    }

    private static void requireAllowed(
            ReceiptLine gift,
            Map<UUID, GiftRule> active,
            ReceiptProducts products
    ) {
        if (gift.giftRuleId() == null) {
            throw new ConflictException("Подарок без акции — уберите строку или добавьте по кнопке подарков");
        }
        GiftRule rule = active.get(gift.giftRuleId());
        if (rule == null) {
            throw new ConflictException("Акция подарка не найдена или выключена — уберите подарок");
        }
        if (!PosGiftValidator.allowsProduct(rule, gift.productId(), products.kindOf(gift.productId()))) {
            throw new ConflictException("Товар не входит в набор акции «" + rule.getName() + "»");
        }
    }

    /** Наборов не больше заработанного, каждого товара — не больше «набор × заработанные подарки». */
    private static void requireWithinEarned(
            GiftRule rule,
            Claim claim,
            int earned,
            ReceiptProducts products
    ) {
        if (claim.groups.size() > earned) {
            throw new ConflictException("Подарков по акции «" + rule.getName()
                    + "» больше положенного — уберите лишний набор");
        }
        claim.qtyByProduct.forEach((productId, qty) -> {
            BigDecimal ceiling = PosGiftValidator.maxQtyPerGift(rule, productId, products.kindOf(productId))
                    .multiply(BigDecimal.valueOf(earned));
            if (qty.compareTo(ceiling) > 0) {
                throw new ConflictException("Подарочного товара по акции «" + rule.getName()
                        + "» больше положенного");
            }
        });
    }

    /** Что чек просит по одной акции: выданные наборы и количество каждого товара. */
    private static final class Claim {
        final Set<String> groups = new HashSet<>();
        final Map<UUID, BigDecimal> qtyByProduct = new HashMap<>();
    }
}
