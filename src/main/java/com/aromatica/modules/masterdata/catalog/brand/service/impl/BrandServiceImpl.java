package com.aromatica.modules.masterdata.catalog.brand.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.masterdata.catalog.brand.model.Brand;
import com.aromatica.modules.masterdata.catalog.brand.repository.BrandRepository;
import com.aromatica.modules.masterdata.catalog.brand.service.BrandService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrandServiceImpl implements BrandService {

    BrandRepository repository;

    @Override
    public Brand find(UUID id) {
        return repository.require(id, "Бренд не найден");
    }

    @Transactional
    @Override
    public Brand create(Brand brand) {
        String trimmed = brand.getName().trim();
        repository.findByNameIgnoreCase(trimmed).ifPresent(b -> {
            throw new BusinessRuleException("Бренд уже есть: " + b.getName());
        });
        brand.setId(UUID.randomUUID());
        brand.setName(trimmed);
        return repository.save(brand);
    }

    @Transactional
    @Override
    public Brand update(
            UUID id,
            Brand changes
    ) {
        String trimmed = changes.getName().trim();
        Brand brand = find(id);
        if (!brand.getName().equals(trimmed)) {
            repository.findByNameIgnoreCase(trimmed).ifPresent(b -> {
                if (!b.getId().equals(id)) {
                    throw new BusinessRuleException("Бренд уже есть: " + b.getName());
                }
            });
            brand.setName(trimmed);
        }
        return brand;
    }

    @Transactional
    @Override
    public Brand setArchived(
            UUID id,
            boolean archived
    ) {
        Brand brand = find(id);
        brand.setArchived(archived);
        return brand;
    }
}
