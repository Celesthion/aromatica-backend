package com.aromatica.modules.inventory.stock.model;

import java.util.Comparator;
import java.util.UUID;

/** Баланс (товар, склад). Блокировки берутся строго в порядке этих ключей — так нет дедлоков. */
public record BalanceKey(
        UUID productId,
        UUID warehouseId
) implements Comparable<BalanceKey> {

    private static final Comparator<BalanceKey> ORDER = Comparator.comparing(BalanceKey::productId)
            .thenComparing(k -> k.warehouseId().toString());

    @Override
    public int compareTo(BalanceKey other) {
        return ORDER.compare(this, other);
    }
}
