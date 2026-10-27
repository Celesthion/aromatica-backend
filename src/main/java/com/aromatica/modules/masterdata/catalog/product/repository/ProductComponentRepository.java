package com.aromatica.modules.masterdata.catalog.product.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductComponentRepository extends EntityRepository<ProductComponent, UUID> {

    List<ProductComponent> findByProductIdInOrderByProductIdAscLineNoAsc(Collection<UUID> productIds);

    void deleteByProductId(UUID productId);

    /** Входит ли товар в состав неархивного комплекта: из-под него товар в архив не убрать. */
    @Query("""
            select count(c) > 0 from ProductComponent c, CatalogProduct p
            where p.id = c.productId and p.archived = false and c.componentId = :componentId""")
    boolean isComponentOfActive(@Param("componentId") UUID componentId);
}
