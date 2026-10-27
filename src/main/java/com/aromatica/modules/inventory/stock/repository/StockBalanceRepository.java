package com.aromatica.modules.inventory.stock.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.inventory.stock.model.StockBalance;
import com.aromatica.modules.inventory.stock.model.StockBalanceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends EntityRepository<StockBalance, StockBalanceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.productId = :productId and b.warehouseId = :warehouseId")
    Optional<StockBalance> findForUpdate(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    /** findAllById по составному ключу шлёт SELECT на каждый товар. */
    @Query("select b from StockBalance b where b.warehouseId = :warehouseId and b.productId in :productIds")
    List<StockBalance> findOnWarehouse(@Param("warehouseId") UUID warehouseId, @Param("productIds") Collection<UUID> productIds);

    @Query("select b.productId from StockBalance b where b.warehouseId = :warehouseId and b.qty > 0 order by b.productId")
    List<UUID> findPositiveProductIds(@Param("warehouseId") UUID warehouseId);

    /** Две параллельные первые приёмки не конфликтуют. */
    @Modifying
    @Query(value = "insert into stock_balance (product_id, warehouse_id) values (:productId, :warehouseId) "
            + "on conflict do nothing", nativeQuery = true)
    void ensureRow(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    @Query("select count(b) from StockBalance b where b.warehouseId = :warehouseId and b.qty <> 0")
    long countNonZeroOnWarehouse(@Param("warehouseId") UUID warehouseId);
}
