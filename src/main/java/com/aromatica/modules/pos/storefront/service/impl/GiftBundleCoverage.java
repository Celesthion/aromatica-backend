package com.aromatica.modules.pos.storefront.service.impl;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хватит ли остатков на весь набор подарков акции: касса показывает только те, что можно
 * выдать сейчас. Без базы и экранных DTO.
 */
final class GiftBundleCoverage {

    private GiftBundleCoverage() {
    }

    /**
     * Позиции могут делить остатки одного товара, поэтому раскладываем их совместно точным
     * перебором: позиций единицы, а жадный алгоритм давал ложные отказы.
     */
    static boolean coverable(
            GiftRule rule,
            Map<UUID, BigDecimal> available,
            Map<String, List<UUID>> idsByKind
    ) {
        Map<UUID, BigDecimal> remaining = new HashMap<>(available);

        // конкретные товары считаем суммарно по каждому (с учётом повторов позиций)
        for (GiftItem it : rule.getItems()) {
            if (it.getKind() != null) {
                continue;
            }
            BigDecimal left = remaining.getOrDefault(it.getProductId(), BigDecimal.ZERO).subtract(it.getQty());
            if (left.signum() < 0) {
                return false;
            }
            remaining.put(it.getProductId(), left);
        }
        // позиции «любой товар формы» раскладываем по товарам формы перебором
        Map<String, List<BigDecimal>> demandByKind = new HashMap<>();
        for (GiftItem it : rule.getItems()) {
            if (it.getKind() != null) {
                demandByKind.computeIfAbsent(it.getKind(), k -> new ArrayList<>()).add(it.getQty());
            }
        }
        for (var e : demandByKind.entrySet()) {
            List<BigDecimal> needs = new ArrayList<>(e.getValue());
            needs.sort(Comparator.reverseOrder()); // крупные первыми: так перебор быстрее отсекает ветки
            if (!assignNeeds(needs, 0, 0, idsByKind.getOrDefault(e.getKey(), List.of()), remaining)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Перебор с откатом. Одинаковые потребности кладём по неубыванию номера товара (from),
     * иначе перебор проходил бы все перестановки и неудача стоила бы факториал.
     */
    private static boolean assignNeeds(
            List<BigDecimal> needs,
            int idx,
            int from,
            List<UUID> ids,
            Map<UUID, BigDecimal> remaining
    ) {
        if (idx == needs.size()) {
            return true;
        }
        BigDecimal need = needs.get(idx);
        int start = idx > 0 && need.compareTo(needs.get(idx - 1)) == 0 ? from : 0;
        for (int i = start; i < ids.size(); i++) {
            UUID id = ids.get(i);
            BigDecimal have = remaining.getOrDefault(id, BigDecimal.ZERO);
            if (have.compareTo(need) >= 0) {
                remaining.put(id, have.subtract(need));
                if (assignNeeds(needs, idx + 1, i, ids, remaining)) {
                    return true;
                }
                remaining.put(id, have);
            }
        }
        return false;
    }
}
