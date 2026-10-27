package com.aromatica.modules.masterdata.catalog.productmodel.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ModelRow;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelDto;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.search.ProductModelSearchRequest;

import java.util.UUID;

public interface ProductModelEndpoint {

    PageResponse<ModelRow> search(ProductModelSearchRequest request);

    ProductModelDto one(UUID id);

    ProductModelDto create(ProductModelRequest request);

    ProductModelDto update(UUID id, ProductModelRequest request);

    ProductModelDto setArchived(UUID id, boolean archived);
}
