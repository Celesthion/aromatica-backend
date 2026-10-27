package com.aromatica.modules.inventory.warehouse.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.inventory.warehouse.model.Warehouse;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends EntityRepository<Warehouse, UUID> {

    Optional<Warehouse> findFirstByIsDefaultTrue();

    /** Полка склада магазина (неархивная). */
    Optional<Warehouse> findFirstByKindAndParentIdAndArchivedFalse(Warehouse.Kind kind, UUID parentId);
}
