package com.aromatica.modules.masterdata.catalog.productmodel.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.repository.ProductModelRepository;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelServiceImpl implements ProductModelService {

    ProductModelRepository repository;

    @Override
    public ProductModel find(UUID id) {
        return repository.require(id, "Продукт не найден");
    }

    @Override
    public ProductModel get(UUID id) {
        return id == null ? null : repository.findById(id).orElse(null);
    }

    @Override
    public List<ProductModel> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }

    @Transactional
    @Override
    public ProductModel create(ProductModel model) {
        normalize(model);
        requireUnique(model.getBrand(), model.getName(), null);
        model.setId(UUID.randomUUID());
        return repository.save(model);
    }

    @Transactional
    @Override
    public ProductModel update(
            UUID id,
            ProductModel changes
    ) {
        normalize(changes);
        requireUnique(changes.getBrand(), changes.getName(), id);
        ProductModel model = find(id);
        model.setBrand(changes.getBrand());
        model.setName(changes.getName());
        model.setDescription(changes.getDescription());
        model.setGender(changes.getGender());
        return model;
    }

    @Transactional
    @Override
    public ProductModel setArchived(
            UUID id,
            boolean archived
    ) {
        ProductModel model = find(id);
        model.setArchived(archived);
        return model;
    }

    /** Пустой бренд храним как null: у служебных продуктов вроде упаковки бренда нет. */
    private static void normalize(ProductModel model) {
        model.setBrand(blankToNull(model.getBrand()));
        model.setName(model.getName().trim());
    }

    /** Пара бренд + название уникальна без учёта регистра; selfId — сам продукт при правке. */
    private void requireUnique(
            String brand,
            String name,
            UUID selfId
    ) {
        (brand == null
                ? repository.findByBrandIsNullAndNameIgnoreCase(name)
                : repository.findByBrandIgnoreCaseAndNameIgnoreCase(brand, name))
                .filter(m -> !m.getId().equals(selfId))
                .ifPresent(m -> {
                    throw new BusinessRuleException("Такой продукт уже есть: "
                            + (m.getBrand() != null ? m.getBrand() + " — " : "") + m.getName());
                });
    }

    @Transactional
    @Override
    public void renameBrand(
            String oldName,
            String newName
    ) {
        repository.renameBrand(oldName, newName);
    }
}
