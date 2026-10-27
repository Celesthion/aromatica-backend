package com.aromatica.modules.pos.sale.service;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Подарки чека заработаны платными строками: правило «подарок от 10 000, ещё один за каждые 10 000». */
@ExtendWith(MockitoExtension.class)
class ReceiptGiftGuardTest {

    @Mock
    private GiftRuleService giftRuleService;

    private final CatalogProduct paid = product();
    private final CatalogProduct present = product();
    private final CatalogProduct other = product();
    private final GiftRule rule = new GiftRule();
    private ReceiptGiftGuard guard;
    private ReceiptProducts products;

    @BeforeEach
    void setUp() {
        rule.setId(UUID.randomUUID());
        rule.setName("Пробник в подарок");
        rule.setStepMinor(10_000L);
        GiftRuleCondition sum = new GiftRuleCondition();
        sum.setType(GiftRuleCondition.Type.SUM);
        sum.setGroupNo(0);
        sum.setMinSumMinor(10_000L);
        rule.getConditions().add(sum);
        GiftItem item = new GiftItem();
        item.setProductId(present.getId());
        item.setQty(BigDecimal.ONE);
        rule.getItems().add(item);
        lenient().when(giftRuleService.activeRules()).thenReturn(List.of(rule));

        guard = new ReceiptGiftGuard(giftRuleService);
        products = new ReceiptProducts(new HashMap<>(Map.of(
                paid.getId(), paid, present.getId(), present, other.getId(), other)), Map.of());
    }

    @Test
    void requireGiftsEarned_skipsReceiptWithoutGifts() {
        guard.requireGiftsEarned(List.of(paidLine("1", 0)), null, products);

        verify(giftRuleService, never()).activeRules();
    }

    @Test
    void requireGiftsEarned_allowsOneGiftPerStep() {
        assertThatCode(() -> guard.requireGiftsEarned(
                List.of(paidLine("2", 0), gift(present, "g1", "1"), gift(present, "g2", "1")), null, products))
                .doesNotThrowAnyException();
    }

    @Test
    void requireGiftsEarned_rejectsGiftWithoutRule() {
        ReceiptLine noRule = new ReceiptLine(present.getId(), BigDecimal.ONE, 0, 0, true, null, "g1", null, null, null);

        assertThatThrownBy(() -> guard.requireGiftsEarned(List.of(paidLine("1", 0), noRule), null, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Подарок без акции");
    }

    @Test
    void requireGiftsEarned_rejectsInactiveRule() {
        ReceiptLine unknownRule = new ReceiptLine(present.getId(), BigDecimal.ONE, 0, 0, true,
                UUID.randomUUID(), "g1", null, null, null);

        assertThatThrownBy(() -> guard.requireGiftsEarned(List.of(paidLine("1", 0), unknownRule), null, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не найдена или выключена");
    }

    @Test
    void requireGiftsEarned_rejectsProductOutsideRuleSet() {
        assertThatThrownBy(() -> guard.requireGiftsEarned(
                List.of(paidLine("1", 0), gift(other, "g1", "1")), null, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("не входит в набор");
    }

    @Test
    void requireGiftsEarned_rejectsMoreSetsThanEarned() {
        assertThatThrownBy(() -> guard.requireGiftsEarned(
                List.of(paidLine("1", 0), gift(present, "g1", "1"), gift(present, "g2", "1")), null, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уберите лишний набор");
    }

    @Test
    void requireGiftsEarned_rejectsQtyAboveSetTimesEarned() {
        assertThatThrownBy(() -> guard.requireGiftsEarned(
                List.of(paidLine("1", 0), gift(present, "g1", "2")), null, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Подарочного товара");
    }

    @Test
    void requireGiftsEarned_capsClaimedBaseAtUndiscountedSum() {
        // клиент заявил 30 000, но платных строк только на 10 000 — второй подарок не положен
        assertThatThrownBy(() -> guard.requireGiftsEarned(
                List.of(paidLine("1", 0), gift(present, "g1", "1"), gift(present, "g2", "1")), 30_000L, products))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("уберите лишний набор");
    }

    @Test
    void requireGiftsEarned_usesClaimedBaseWithinPaidRange() {
        // после скидки строки 15 000 — один подарок; база до скидки чека 20 000 честная — два
        List<ReceiptLine> lines = List.of(paidLine("2", 5_000), gift(present, "g1", "1"), gift(present, "g2", "1"));

        assertThatThrownBy(() -> guard.requireGiftsEarned(lines, null, products))
                .isInstanceOf(ConflictException.class);
        assertThatCode(() -> guard.requireGiftsEarned(lines, 20_000L, products))
                .doesNotThrowAnyException();
    }

    private ReceiptLine paidLine(
            String qty,
            long discountMinor
    ) {
        return new ReceiptLine(paid.getId(), new BigDecimal(qty), 10_000, discountMinor, false, null, null, null, null, null);
    }

    private ReceiptLine gift(
            CatalogProduct product,
            @Nullable String group,
            String qty
    ) {
        return new ReceiptLine(product.getId(), new BigDecimal(qty), 0, 0, true, rule.getId(), group, null, null, null);
    }

    private static CatalogProduct product() {
        CatalogProduct p = new CatalogProduct();
        p.setId(UUID.randomUUID());
        p.setKind(CatalogProduct.Kind.BOTTLE.name());
        p.setUnit(CatalogProduct.Unit.PCS);
        return p;
    }
}
