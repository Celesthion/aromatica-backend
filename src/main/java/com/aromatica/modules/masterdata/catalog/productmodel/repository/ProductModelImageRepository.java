package com.aromatica.modules.masterdata.catalog.productmodel.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.masterdata.catalog.productmodel.model.MainImageRow;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImage;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImageSummary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductModelImageRepository extends EntityRepository<ProductModelImage, UUID> {

    /** Галерея продукта без байтов фото. */
    List<ProductModelImageSummary> findSummariesByModelIdOrderBySortAscCreatedAtAsc(UUID modelId);

    /** Метаданные одного фото без байтов. */
    Optional<ProductModelImageSummary> findSummaryById(UUID id);

    /** id фото продукта в порядке галереи. */
    @Query("select i.id from ProductModelImage i where i.modelId = :modelId order by i.sort, i.createdAt")
    List<UUID> findIdsByModelId(@Param("modelId") UUID modelId);

    // Правки флага и удаление — запросами: сущность целиком подняла бы байты фото

    @Modifying
    @Query("update ProductModelImage i set i.main = false where i.modelId = :modelId and i.main = true")
    void clearMain(@Param("modelId") UUID modelId);

    @Modifying
    @Query("update ProductModelImage i set i.main = true where i.id = :id")
    void setMain(@Param("id") UUID id);

    @Modifying
    @Query("delete from ProductModelImage i where i.id = :id")
    void deleteWithoutLoading(@Param("id") UUID id);

    /** Основные фото пачкой: продукт → id картинки, без байтов. */
    @Query("select i.modelId as modelId, i.id as imageId from ProductModelImage i where i.modelId in :ids and i.main = true")
    List<MainImageRow> findMainIdsByModelIds(@Param("ids") Collection<UUID> ids);

    long countByModelId(UUID modelId);

    @Query("select coalesce(max(i.sort), -1) from ProductModelImage i where i.modelId = :modelId")
    int maxSort(@Param("modelId") UUID modelId);
}
