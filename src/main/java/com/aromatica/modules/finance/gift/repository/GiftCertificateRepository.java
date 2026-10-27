package com.aromatica.modules.finance.gift.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiftCertificateRepository extends BaseRepository<GiftCertificate, UUID> {

    Optional<GiftCertificate> findByCode(String code);

    List<GiftCertificate> findByCodeIn(List<String> codes);

    /** Их деньги идут в пересчёт кассы, но не в выручку. */
    List<GiftCertificate> findBySoldShiftId(UUID shiftId);

    long countByStatus(GiftCertificate.Status status);

    @Query("select coalesce(sum(c.balanceMinor), 0) from GiftCertificate c where c.status = :status")
    long sumBalanceByStatus(@Param("status") GiftCertificate.Status status);
}
