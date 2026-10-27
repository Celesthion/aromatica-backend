package com.aromatica.modules.inventory.stock.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends EntityRepository<StockMovement, Long> {

    List<StockMovement> findByDocId(UUID docId);

    @Query(value = "select nextval('stock_movement_id_seq') from generate_series(1, :n)", nativeQuery = true)
    List<Long> nextIds(@Param("n") int n);

    interface BalanceFold {
        BigDecimal getQty();

        long getAvgCostMinor();
    }

    /**
     * Повторяет {@code CostMath.newAvgCostMinor} — менять синхронно (round в Postgres тоже HALF_UP).
     * Порядок — проведения (id), не moment: документ задним числом считался по тогдашнему остатку.
     */
    @Query(value = """
            with recursive fold(id, qty, avg_cost) as (
                select 0::bigint, 0::numeric, 0::bigint
                union all
                select m.id, f.qty + m.qty_delta,
                       case when m.qty_delta <= 0 then f.avg_cost
                            when f.qty <= 0 then m.cost_minor
                            else round((f.qty * f.avg_cost + m.qty_delta * m.cost_minor) / (f.qty + m.qty_delta))::bigint
                       end
                from fold f
                cross join lateral (
                    select s.id, s.qty_delta, s.cost_minor from stock_movement s
                    where s.product_id = :productId and s.warehouse_id = :warehouseId and s.id > f.id
                    order by s.id
                    limit 1
                ) m
            )
            select qty, avg_cost as avgCostMinor from fold order by id desc limit 1
            """, nativeQuery = true)
    BalanceFold foldBalance(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    /** Движения копятся годами — только страницами. */
    Slice<StockMovement> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId, Pageable pageable);

    @Modifying
    @Query("delete from StockMovement m where m.docId = :docId")
    int deleteByDocId(@Param("docId") UUID docId);
}
