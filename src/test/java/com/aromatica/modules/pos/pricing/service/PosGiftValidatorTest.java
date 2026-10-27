package com.aromatica.modules.pos.pricing.service;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.pos.pricing.service.PosGiftValidator.PaidLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.aromatica.TestIds.id;
import static org.assertj.core.api.Assertions.assertThat;

/** Движок подарков на сервере. Считать он должен так же, как фронтовый gifts.ts. */
class PosGiftValidatorTest {

    private GiftRuleCondition sum(
            long minSumMinor,
            int group
    ) {
        GiftRuleCondition c = new GiftRuleCondition();
        c.setType(GiftRuleCondition.Type.SUM);
        c.setGroupNo(group);
        c.setMinSumMinor(minSumMinor);
        return c;
    }

    private GiftRuleCondition kind(
            String k,
            BigDecimal minVol,
            int group
    ) {
        GiftRuleCondition c = new GiftRuleCondition();
        c.setType(GiftRuleCondition.Type.KIND);
        c.setGroupNo(group);
        c.setKind(k);
        c.setMinVolumeMl(minVol);
        return c;
    }

    private GiftRuleCondition product(
            UUID id,
            int group
    ) {
        GiftRuleCondition c = new GiftRuleCondition();
        c.setType(GiftRuleCondition.Type.PRODUCT);
        c.setGroupNo(group);
        c.setProductId(id);
        return c;
    }

    private GiftRule rule(
            List<GiftRuleCondition> conds,
            Long step,
            Integer max,
            List<GiftItem> items
    ) {
        GiftRule r = new GiftRule();
        r.setId(UUID.randomUUID());
        r.setName("тест");
        r.getConditions().addAll(conds);
        r.setStepMinor(step);
        r.setMaxGifts(max);
        if (items != null) {
            r.getItems().addAll(items);
        }
        return r;
    }

    private PaidLine pcs(
            UUID id,
            String kind,
            String qty,
            String volumeMl
    ) {
        return new PaidLine(id, kind, false, new BigDecimal(qty),
                volumeMl == null ? null : new BigDecimal(volumeMl));
    }

    private PaidLine ml(
            UUID id,
            String kind,
            String qtyMl
    ) {
        return new PaidLine(id, kind, true, new BigDecimal(qtyMl), null);
    }

    @Test
    void sumCondition_earnsOneWhenReached() {
        GiftRule r = rule(List.of(sum(100_000, 0)), null, null, null);
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 99_999)).isZero();
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 100_000)).isEqualTo(1);
    }

    @Test
    void earnedGiftCount_stepMultipliesOverHighestSumThreshold() {
        GiftRule r = rule(List.of(sum(100_000, 0)), 50_000L, null, null);
        // от 100k один подарок и ещё по одному за каждые 50k сверх порога
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 100_000)).isEqualTo(1);
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 150_000)).isEqualTo(2);
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 260_000)).isEqualTo(4);
    }

    @Test
    void earnedGiftCount_capsAtMaxGifts() {
        GiftRule r = rule(List.of(sum(100_000, 0)), 50_000L, 2, null);
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(), 300_000)).isEqualTo(2);
    }

    @Test
    void earnedGiftCount_groupsAreAndWithinOrBetween() {
        // группа 0: SUM≥100k и товар X, группа 1: форма BOTTLE. Хватает одной группы
        GiftRule r = rule(List.of(sum(100_000, 0), product(id("X"), 0), kind("BOTTLE", null, 1)),
                null, null, null);
        // только сумма: в группе 0 нет X, в группе 1 нет BOTTLE, подарков 0
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(pcs(id("Y"), "TESTER", "1", null)), 200_000))
                .isZero();
        // есть BOTTLE, группа 1 выполнена
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(pcs(id("Z"), "BOTTLE", "1", null)), 0))
                .isEqualTo(1);
        // сумма и товар X, группа 0 выполнена
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(pcs(id("X"), "TESTER", "1", null)), 100_000))
                .isEqualTo(1);
    }

    @Test
    void kindVolume_mlUsesPouredQty_pcsUsesProductVolume() {
        GiftRule r = rule(List.of(kind("SPLIT", new BigDecimal("10"), 0)), null, null, null);
        // у наливного объём — налитые мл строки
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(ml(id("s"), "SPLIT", "9")), 0)).isZero();
        assertThat(PosGiftValidator.earnedGiftCount(r, List.of(ml(id("s"), "SPLIT", "10")), 0)).isEqualTo(1);
        // у штучного объём берётся из товара
        GiftRule r2 = rule(List.of(kind("BOTTLE", new BigDecimal("50"), 0)), null, null, null);
        assertThat(PosGiftValidator.earnedGiftCount(r2, List.of(pcs(id("b"), "BOTTLE", "1", "30")), 0)).isZero();
        assertThat(PosGiftValidator.earnedGiftCount(r2, List.of(pcs(id("b"), "BOTTLE", "1", "75")), 0)).isEqualTo(1);
    }

    @Test
    void allowsProduct_bySpecificOrKind() {
        GiftItem specific = new GiftItem();
        specific.setProductId(id("gift-1"));
        specific.setQty(BigDecimal.ONE);
        GiftItem anyKind = new GiftItem();
        anyKind.setKind("SPLIT");
        anyKind.setQty(new BigDecimal("5"));
        GiftRule r = rule(List.of(sum(1, 0)), null, null, List.of(specific, anyKind));

        assertThat(PosGiftValidator.allowsProduct(r, id("gift-1"), "BOTTLE")).isTrue();
        assertThat(PosGiftValidator.allowsProduct(r, id("other"), "SPLIT")).isTrue();
        assertThat(PosGiftValidator.allowsProduct(r, id("other"), "BOTTLE")).isFalse();
        assertThat(PosGiftValidator.maxQtyPerGift(r, id("other"), "SPLIT")).isEqualByComparingTo("5");
    }
}
