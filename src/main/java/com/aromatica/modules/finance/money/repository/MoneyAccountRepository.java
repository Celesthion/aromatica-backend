package com.aromatica.modules.finance.money.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.finance.money.model.MoneyAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MoneyAccountRepository extends EntityRepository<MoneyAccount, UUID> {

    List<MoneyAccount> findByArchivedFalseOrderByCreatedAtAsc();

    /** Default, иначе самый старый: выбор не должен зависеть от порядка строк в БД. */
    Optional<MoneyAccount> findFirstByTypeAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(MoneyAccount.Type type);
}
