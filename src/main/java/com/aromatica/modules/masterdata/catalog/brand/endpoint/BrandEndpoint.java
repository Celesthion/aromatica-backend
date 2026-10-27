package com.aromatica.modules.masterdata.catalog.brand.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandDto;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandRequest;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSearchRequest;

import java.util.UUID;

public interface BrandEndpoint {

    PageResponse<BrandDto> search(BrandSearchRequest request);

    BrandDto create(BrandRequest request);

    BrandDto update(UUID id, BrandRequest request);

    BrandDto setArchived(UUID id, boolean archived);
}
