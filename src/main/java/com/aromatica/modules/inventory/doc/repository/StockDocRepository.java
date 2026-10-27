package com.aromatica.modules.inventory.doc.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.inventory.doc.model.DocLineCountRow;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.SalesCountRow;
import com.aromatica.modules.inventory.doc.model.SalesGroupRow;
import com.aromatica.modules.inventory.doc.model.SellerStatsRow;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.SupplierDebtRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockDocRepository extends BaseRepository<StockDoc, UUID> {

    /** Следующий сквозной номер документа данного типа (sequence из V3). */
    @Query(value = "SELECT nextval('stock_doc_number_' || lower(:type))", nativeQuery = true)
    long nextNumber(@Param("type") String type);

    @Query(value = """
            select coalesce(sum(sum_minor), 0) as sold_minor,
                   count(*) as sales_count,
                   count(distinct date(moment)) as days_worked
            from stock_doc
            where created_by = :userId and doc_type = 'SALE' and status = 'POSTED'
            """, nativeQuery = true)
    SellerStatsRow sellerStats(@Param("userId") UUID userId);

    /**
     * Формулы сумм зеркалят StockDocLine (CostMath) — менять синхронно; round в Postgres
     * округляет половину вверх, как HALF_UP.
     */
    @Query(value = """
            select date(d.moment) as day, l.product_id as productId, d.created_by as sellerId,
                   d.doc_type as docType, l.is_gift as gift,
                   sum(greatest(0, round(l.qty * l.price_minor) - l.discount_minor)) as revenueMinor,
                   sum(coalesce(round(l.qty * l.cost_minor), 0)) as costMinor,
                   sum(l.qty) as qty,
                   count(*) as lineCount
            from stock_doc d join stock_doc_line l on l.doc_id = d.id
            where d.status = 'POSTED' and d.doc_type in ('SALE', 'RETURN')
              and d.moment >= :from and d.moment < :to
            group by 1, 2, 3, 4, 5
            """, nativeQuery = true)
    List<SalesGroupRow> salesGroups(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
            select date(moment) as day, created_by as sellerId, count(*) as sales
            from stock_doc
            where status = 'POSTED' and doc_type = 'SALE' and moment >= :from and moment < :to
            group by 1, 2
            """, nativeQuery = true)
    List<SalesCountRow> salesCounts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Число строк документов — списку не нужно грузить сами строки. */
    @Query("select l.doc.id as docId, count(l) as lineCount from StockDocLine l where l.doc.id in :ids group by l.doc.id")
    List<DocLineCountRow> countLines(@Param("ids") Collection<UUID> ids);

    List<StockDoc> findByShiftIdOrderByMomentDescNumberDesc(UUID shiftId);

    List<StockDoc> findByShiftIdAndStatusAndDocTypeInOrderByMomentDescNumberDesc(
            UUID shiftId, DocStatus status, Collection<DocType> types);

    @Query("""
            select d.counterpartyId as counterpartyId,
                   sum(case when d.docType = :supply then d.sumMinor else -d.sumMinor end) as debtMinor
            from StockDoc d
            where d.docType in :types and d.status = :posted and d.payAccountId is null
            group by d.counterpartyId
            having sum(case when d.docType = :supply then d.sumMinor else -d.sumMinor end) <> 0""")
    List<SupplierDebtRow> sumUnpaidByCounterparty(
            @Param("types") Collection<DocType> types,
            @Param("supply") DocType supply,
            @Param("posted") DocStatus posted);

    List<StockDoc> findBySourceDocIdAndStatus(UUID sourceDocId, DocStatus status);

    List<StockDoc> findByCounterpartyIdAndDocTypeAndStatusOrderByMomentDescNumberDesc(UUID counterpartyId, DocType docType, DocStatus status, Pageable pageable);
}
