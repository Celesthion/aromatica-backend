package com.aromatica.modules.inventory.stock.service.impl;

import com.aromatica.common.search.Paging;
import com.aromatica.modules.inventory.stock.model.BalanceKey;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import com.aromatica.modules.inventory.stock.repository.StockBalanceRepository;
import com.aromatica.modules.inventory.stock.repository.StockMovementRepository.BalanceFold;
import com.aromatica.modules.inventory.stock.repository.StockMovementRepository;
import com.aromatica.modules.inventory.stock.service.StockLedger;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockLedgerImpl implements StockLedger {

    StockBalanceRepository balanceRepository;
    StockMovementRepository movementRepository;
    WarehouseLookup warehouseLookup;
    EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public Map<BalanceKey, StockBalance> lock(Collection<BalanceKey> keys) {
        Map<BalanceKey, StockBalance> locked = new LinkedHashMap<>();
        new TreeSet<>(keys).forEach(key -> locked.put(key, lockOrCreate(key)));
        return locked;
    }

    /**
     * id берём после блокировок балансов: по id восстанавливается порядок проведения (пересчёт
     * средней), поэтому заранее блоками (allocationSize) их выдавать нельзя.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void record(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return;
        }
        List<Long> ids = movementRepository.nextIds(movements.size());
        for (int i = 0; i < movements.size(); i++) {
            movements.get(i).setId(ids.get(i));
            entityManager.persist(movements.get(i));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void revert(UUID docId) {
        // блокируем в том же порядке, что и проведение, иначе дедлок
        Set<BalanceKey> affected = new TreeSet<>();
        for (StockMovement m : movementRepository.findByDocId(docId)) {
            affected.add(new BalanceKey(m.getProductId(), m.getWarehouseId()));
        }
        movementRepository.deleteByDocId(docId);
        affected.forEach(this::rebuild);
    }

    /** Пересчёт по оставшемуся журналу свёрткой в базе, в порядке проведения. */
    private void rebuild(BalanceKey key) {
        StockBalance balance = lockOrCreate(key);
        BalanceFold fold = movementRepository.foldBalance(key.productId(), key.warehouseId());
        balance.setQty(fold.getQty());
        balance.setAvgCostMinor(fold.getAvgCostMinor());
    }

    private StockBalance lockOrCreate(BalanceKey key) {
        StockBalance balance = balanceRepository.findForUpdate(key.productId(), key.warehouseId()).orElseGet(() -> {
            // FOR UPDATE не блокирует несуществующую строку: вставляем идемпотентно и перечитываем под локом
            balanceRepository.ensureRow(key.productId(), key.warehouseId());
            return balanceRepository.findForUpdate(key.productId(), key.warehouseId()).orElseThrow();
        });
        // запрос с блокировкой отдаёт объект из сессии со старым количеством (касса уже читала
        // остатки) — два чека продавали последний предмет; refresh перечитывает под блокировкой
        entityManager.refresh(balance, LockModeType.PESSIMISTIC_WRITE);
        return balance;
    }

    @Override
    public Slice<StockMovement> movements(
            UUID productId,
            UUID warehouseId,
            int page,
            int size
    ) {
        UUID wh = warehouseId != null ? warehouseId : warehouseLookup.defaultWarehouseId();
        return movementRepository.findByProductIdAndWarehouseId(productId, wh,
                PageRequest.of(Paging.page(page), Paging.size(size),
                        Sort.by(Sort.Order.desc("moment"), Sort.Order.desc("id"))));
    }
}
