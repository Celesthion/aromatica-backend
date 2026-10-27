package com.aromatica.modules.inventory.stock.service.impl;

import com.aromatica.modules.inventory.stock.repository.StockBalanceRepository;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockBalanceServiceImpl implements StockBalanceService {

    StockBalanceRepository repository;

    @Override
    public Map<UUID, BigDecimal> qtyMap(
            UUID warehouseId,
            Collection<UUID> productIds
    ) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        repository.findOnWarehouse(warehouseId, Set.copyOf(productIds))
                .forEach(b -> out.put(b.getProductId(), b.getQty()));
        return out;
    }

    @Transactional
    @Override
    public void lockAll(
            UUID warehouseId,
            Collection<UUID> productIds
    ) {
        // строки, которой нет, блокировать нечем: такого товара на складе ноль, бронь его не возьмёт
        productIds.stream().distinct().sorted()
                .forEach(productId -> repository.findForUpdate(productId, warehouseId));
    }

    @Override
    public boolean hasStock(UUID warehouseId) {
        return repository.countNonZeroOnWarehouse(warehouseId) > 0;
    }

    @Override
    public List<UUID> positiveProductIds(UUID warehouseId) {
        return repository.findPositiveProductIds(warehouseId);
    }

}
