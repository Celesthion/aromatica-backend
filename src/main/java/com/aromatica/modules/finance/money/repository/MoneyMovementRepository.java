package com.aromatica.modules.finance.money.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.finance.money.model.AccountBalanceRow;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MoneyMovementRepository extends EntityRepository<MoneyMovement, Long> {

    /** Slice — без подсчёта всех строк. */
    Slice<MoneyMovement> findAllByOrderByMomentDescIdDesc(Pageable pageable);

    /** Отдельный запрос, а не «счёт is null or …», чтобы шёл по индексу счёта. */
    Slice<MoneyMovement> findByAccountIdOrderByMomentDescIdDesc(UUID accountId, Pageable pageable);

    @Query("""
            select m.accountId as accountId, coalesce(sum(m.amountMinor), 0) as balanceMinor
            from MoneyMovement m group by m.accountId""")
    List<AccountBalanceRow> balancesByAccount();

    List<MoneyMovement> findByDocId(UUID docId);

    @Modifying
    @Query("delete from MoneyMovement m where m.docId = :docId")
    int deleteByDocId(@Param("docId") UUID docId);
}
