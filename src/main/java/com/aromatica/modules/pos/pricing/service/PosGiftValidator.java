package com.aromatica.modules.pos.pricing.service;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Серверная копия gifts.ts: флаг gift ставит клиент, иначе чек из одних подарков списал бы товар
 * без выручки. Без БД, чтобы тестировать так же, как gifts.ts.
 */
public final class PosGiftValidator {

    private PosGiftValidator() {
    }

    public record PaidLine(
            UUID productId,
            String kind,
            boolean isMl,
            BigDecimal qty,
            BigDecimal volumeMl
    ) {}

    /** paid — уже без подарочных строк. */
    static boolean condMet(
            GiftRuleCondition c,
            List<PaidLine> paid,
            long baseMinor
    ) {
        switch (c.getType()) {
            case SUM:
                return c.getMinSumMinor() != null && baseMinor >= c.getMinSumMinor();
            case PRODUCT:
                return paid.stream().anyMatch(l -> l.productId().equals(c.getProductId()));
            case KIND:
                return paid.stream().anyMatch(l -> {
                    if (!l.kind().equals(c.getKind())) {
                        return false;
                    }
                    if (c.getMinVolumeMl() == null) {
                        return true;
                    }
                    // у наливных объём — налитые мл, у штучных — объём товара
                    BigDecimal vol = l.isMl() ? l.qty()
                            : (l.volumeMl() != null ? l.volumeMl() : BigDecimal.ZERO);
                    return vol.compareTo(c.getMinVolumeMl()) >= 0;
                });
            default:
                return false;
        }
    }

    /**
     * Группы условий: «И» внутри, «ИЛИ» между. Шаг stepMinor считается сверх наибольшего
     * выполненного SUM-порога, сверху ограничено maxGifts.
     */
    public static int earnedGiftCount(
            GiftRule r,
            List<PaidLine> paid,
            long baseMinor
    ) {
        if (r.getConditions().isEmpty()) {
            return 0;
        }
        boolean matched = r.getConditions().stream().map(GiftRuleCondition::getGroupNo).distinct()
                .anyMatch(g -> r.getConditions().stream()
                        .filter(c -> c.getGroupNo() == g)
                        .allMatch(c -> condMet(c, paid, baseMinor)));
        if (!matched) {
            return 0;
        }
        int n = 1;
        // от меньшего порога подарков вышло бы больше положенного
        Long sumBase = r.getConditions().stream()
                .filter(c -> c.getType() == GiftRuleCondition.Type.SUM
                        && c.getMinSumMinor() != null && baseMinor >= c.getMinSumMinor())
                .map(GiftRuleCondition::getMinSumMinor)
                .max(Long::compareTo).orElse(null);
        if (sumBase != null && r.getStepMinor() != null && r.getStepMinor() > 0) {
            n += (int) Math.max(0, (baseMinor - sumBase) / r.getStepMinor());
        }
        return r.getMaxGifts() != null ? Math.min(n, r.getMaxGifts()) : n;
    }

    /** Товар подходит, если указан позицией набора или подпадает под «любой товар формы». */
    public static boolean allowsProduct(
            GiftRule r,
            UUID productId,
            String kind
    ) {
        return r.getItems().stream().anyMatch(it -> matchesItem(it, productId, kind));
    }

    /** Сколько единиц товара максимум можно дать за один подарок. */
    public static BigDecimal maxQtyPerGift(
            GiftRule r,
            UUID productId,
            String kind
    ) {
        return r.getItems().stream()
                .filter(it -> matchesItem(it, productId, kind))
                .map(GiftItem::getQty)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private static boolean matchesItem(
            GiftItem it,
            UUID productId,
            String kind
    ) {
        if (it.getProductId() != null) {
            return it.getProductId().equals(productId);
        }
        return it.getKind() != null && it.getKind().equals(kind);
    }
}
