package com.aromatica.modules.inventory.stock.service;

import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import org.springframework.data.domain.Slice;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Журнал остатков: остатки пишет только он, и только внутри транзакции проведения. */
public interface StockLedger {

    /** Строго в порядке ключей, чтобы не взять блокировки накрест; нет баланса — создаёт нулевой. */
    Map<BalanceKey, StockBalance> lock(Collection<BalanceKey> keys);

    /** id — из последовательности, в порядке проведения. */
    void record(List<StockMovement> movements);

    /** Свежие сверху; склад null — склад по умолчанию. */
    Slice<StockMovement> movements(UUID productId, UUID warehouseId, int page, int size);

    void revert(UUID docId);
}
