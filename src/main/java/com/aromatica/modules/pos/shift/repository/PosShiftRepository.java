package com.aromatica.modules.pos.shift.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.pos.shift.model.PosShift;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosShiftRepository extends BaseRepository<PosShift, UUID> {

    Optional<PosShift> findFirstByWarehouseIdAndStatus(UUID warehouseId, PosShift.Status status);

    /** Один человек держит открытой только одну кассу за раз. */
    Optional<PosShift> findFirstByOpenedByAndStatus(UUID openedBy, PosShift.Status status);

    List<PosShift> findByStatusOrderByOpenedAtDesc(PosShift.Status status);

    @Query(value = "SELECT nextval('pos_shift_number')", nativeQuery = true)
    long nextNumber();
}
