package com.aromatica.modules.masterdata.catalog.product.service.impl;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.repository.CatalogProductRepository;
import com.aromatica.modules.masterdata.catalog.product.repository.ProductComponentRepository;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    CatalogProductRepository repository;
    ProductComponentRepository componentRepository;

    @Override
    public CatalogProduct find(UUID id) {
        return repository.require(id, "Товар не найден");
    }

    @Override
    public boolean exists(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public List<CatalogProduct> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public List<CatalogProduct> findByModelId(UUID modelId) {
        return repository.findByModelId(modelId);
    }

    @Override
    public Map<UUID, Long> countByModelIds(Collection<UUID> ids) {
        Map<UUID, Long> out = new HashMap<>();
        if (!ids.isEmpty()) {
            repository.countByModelIds(ids).forEach(row -> out.put(row.getModelId(), row.getProducts()));
        }
        return out;
    }

    @Override
    public List<CatalogProduct> findActiveByKind(String kind) {
        return repository.findByKindAndArchivedFalseOrderByNameAsc(kind);
    }

    @Override
    public List<CatalogProduct> findActiveByKinds(Collection<String> kinds) {
        return kinds.isEmpty() ? List.of() : repository.findByKindInAndArchivedFalse(kinds);
    }

    @Override
    public String nameOf(UUID id) {
        return repository.findById(id).map(CatalogProduct::getName).orElse(id.toString());
    }

    @Override
    public Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        repository.findNames(Set.copyOf(ids)).forEach(row -> names.put(row.getId(), row.getName()));
        return names;
    }

    @Transactional
    @Override
    public CatalogProduct save(CatalogProduct product) {
        return repository.save(product);
    }

    @Override
    public Map<UUID, List<ProductComponent>> componentsOf(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return componentRepository.findByProductIdInOrderByProductIdAscLineNoAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductComponent::getProductId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    @Override
    public void replaceComponents(
            UUID productId,
            List<ProductComponent> components
    ) {
        componentRepository.deleteByProductId(productId);
        componentRepository.flush();
        int lineNo = 1;
        for (ProductComponent c : components) {
            c.setId(UUID.randomUUID());
            c.setProductId(productId);
            c.setLineNo(lineNo++);
        }
        componentRepository.saveAll(components);
    }

    @Override
    public boolean isComponentOfActive(UUID productId) {
        return componentRepository.isComponentOfActive(productId);
    }

    @Override
    public Optional<CatalogProduct> findByCompositionKey(String key) {
        return repository.findFirstByCompositionKeyAndArchivedFalse(key);
    }

    @Transactional
    @Override
    public String nextArticle() {
        return String.format("%08d", repository.nextArticleNumber());
    }

    @Transactional
    @Override
    public int recomposeNamesForModel(UUID modelId) {
        return repository.recomposeNames(modelId.toString(), null, null);
    }

    @Transactional
    @Override
    public int recomposeNamesForBrand(String brandName) {
        return repository.recomposeNames(null, null, brandName);
    }

    @Transactional
    @Override
    public int recomposeNamesForKind(String kindCode) {
        return repository.recomposeNames(null, kindCode, null);
    }
}
