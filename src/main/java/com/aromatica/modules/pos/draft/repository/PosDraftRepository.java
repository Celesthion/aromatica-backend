package com.aromatica.modules.pos.draft.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.pos.draft.model.PosDraft;

import java.util.List;
import java.util.UUID;

public interface PosDraftRepository extends EntityRepository<PosDraft, UUID> {

    List<PosDraft> findByShiftIdOrderByCreatedAtDesc(UUID shiftId);

    long countByShiftId(UUID shiftId);
}
