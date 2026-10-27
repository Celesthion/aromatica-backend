package com.aromatica.modules.masterdata.catalog.product.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ModelProductCountRow;
import com.aromatica.modules.masterdata.catalog.product.model.ProductNameRow;
import com.aromatica.modules.masterdata.catalog.product.model.ProductNaming;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogProductRepository extends BaseRepository<CatalogProduct, UUID> {

    /** Готовое с таким же составом: на один состав одна карточка. */
    Optional<CatalogProduct> findFirstByCompositionKeyAndArchivedFalse(String compositionKey);

    /** Формы (SKU) аромата. */
    List<CatalogProduct> findByModelId(UUID modelId);

    /** Неархивные товары формы, например упаковка для кассы. */
    List<CatalogProduct> findByKindAndArchivedFalseOrderByNameAsc(String kind);

    List<CatalogProduct> findByKindInAndArchivedFalse(Collection<String> kinds);

    /** Число форм по продуктам, одним запросом на страницу списка. */
    @Query("select p.modelId as modelId, count(p) as products from CatalogProduct p where p.modelId in :ids group by p.modelId")
    List<ModelProductCountRow> countByModelIds(@Param("ids") Collection<UUID> ids);

    /** Имена товаров без загрузки сущностей. */
    @Query("select p.id as id, p.name as name from CatalogProduct p where p.id in :ids")
    List<ProductNameRow> findNames(@Param("ids") Collection<UUID> ids);

    /** Следующий системный номер артикула (sequence из миграции). */
    @Query(value = "SELECT nextval('product_article_seq')", nativeQuery = true)
    long nextArticleNumber();

    /**
     * SQL-зеркало ProductNaming.compose — менять синхронно. Распив (SPLIT) не трогаем:
     * его имя могли задать руками при вскрытии.
     */
    @Modifying
    @Query(value = """
            update product p
            set name = concat_ws(' · ',
                    case when m.brand is not null then m.brand || ' — ' || m.name else m.name end,
                    k.name,
                    nullif(p.label, ''),
                    case when p.volume_ml is not null then trim_scale(p.volume_ml)::text || ' мл' end)
            from product_model m, product_kind k
            where m.id = p.model_id and k.code = p.kind and p.kind <> 'SPLIT'
              and (:modelId is null or p.model_id = cast(:modelId as uuid))
              and (:kind is null or p.kind = :kind)
              and (:brand is null or m.brand = :brand)
            """, nativeQuery = true)
    int recomposeNames(@Param("modelId") String modelId, @Param("kind") String kind, @Param("brand") String brand);
}
