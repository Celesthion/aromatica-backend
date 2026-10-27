package com.aromatica.modules.masterdata.catalog.brand.repository;

import com.aromatica.common.search.BaseRepository;
import com.aromatica.modules.masterdata.catalog.brand.model.Brand;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends BaseRepository<Brand, UUID> {

    Optional<Brand> findByNameIgnoreCase(String name);
}
