package com.aromatica.modules.masterdata.catalog.productkind.endpoint;

import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindDto;
import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindRequest;

import java.util.List;

public interface ProductKindEndpoint {

    List<KindDto> list(boolean includeArchived);

    KindDto create(KindRequest request);

    KindDto update(String code, KindRequest request);

    KindDto setArchived(String code, boolean archived);
}
