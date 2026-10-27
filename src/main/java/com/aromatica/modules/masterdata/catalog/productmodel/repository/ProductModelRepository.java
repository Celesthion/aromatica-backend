package com.aromatica.modules.masterdata.catalog.productmodel.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ProductModelRepository extends BaseRepository<ProductModel, UUID> {

    Optional<ProductModel> findByBrandIgnoreCaseAndNameIgnoreCase(String brand, String name);

    /** Уникальность служебных продуктов без бренда, вроде упаковки. */
    Optional<ProductModel> findByBrandIsNullAndNameIgnoreCase(String name);

    /** При переименовании бренда: продукты хранят его имя строкой. */
    @Transactional
    @Modifying
    @Query("update ProductModel m set m.brand = :newName where m.brand = :oldName")
    int renameBrand(@Param("oldName") String oldName, @Param("newName") String newName);
}
