package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Товары: чтение, сохранение, состав, имена. Поиск — в {@link ProductSearchService}. */
public interface ProductService {

    CatalogProduct find(UUID id);

    boolean exists(UUID id);

    List<CatalogProduct> findAllById(Collection<UUID> ids);

    List<CatalogProduct> findByModelId(UUID modelId);

    /** Число SKU по продуктам: modelId → количество. */
    Map<UUID, Long> countByModelIds(Collection<UUID> ids);

    List<CatalogProduct> findActiveByKind(String kind);

    List<CatalogProduct> findActiveByKinds(Collection<String> kinds);

    /** Имя товара для сообщений об ошибках; если не найден — сам id. */
    String nameOf(UUID id);

    /** Имена товаров одним запросом: id → имя. Ненайденных ключей нет. */
    Map<UUID, String> names(Collection<UUID> ids);

    CatalogProduct save(CatalogProduct product);

    /** Состав пачкой: productId → строки состава по порядку. У товаров без состава ключа нет. */
    Map<UUID, List<ProductComponent>> componentsOf(Collection<UUID> productIds);

    /** Заменяет состав целиком, в порядке списка. */
    void replaceComponents(UUID productId, List<ProductComponent> components);

    boolean isComponentOfActive(UUID productId);

    /** Неархивное готовое с таким ключом состава. */
    Optional<CatalogProduct> findByCompositionKey(String key);

    /** Системный артикул: последовательный номер из 8 цифр с ведущими нулями, после создания не меняется. */
    String nextArticle();

    int recomposeNamesForModel(UUID modelId);

    int recomposeNamesForBrand(String brandName);

    int recomposeNamesForKind(String kindCode);
}
