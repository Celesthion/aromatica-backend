package com.aromatica.modules.masterdata.party.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CounterpartyRepository extends BaseRepository<Counterparty, UUID> {

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    /** id контрагентов, в имени которых есть подстрока (без учёта регистра); архивные тоже. */
    @Query("select c.id from Counterparty c where lower(c.name) like lower(concat('%', :part, '%'))")
    List<UUID> findIdsByNameContaining(@Param("part") String part);
}
