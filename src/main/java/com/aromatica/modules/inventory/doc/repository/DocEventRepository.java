package com.aromatica.modules.inventory.doc.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.inventory.doc.model.DocEvent;

import java.util.List;
import java.util.UUID;

public interface DocEventRepository extends EntityRepository<DocEvent, Long> {

    List<DocEvent> findByDocIdOrderByAtAscIdAsc(UUID docId);
}
