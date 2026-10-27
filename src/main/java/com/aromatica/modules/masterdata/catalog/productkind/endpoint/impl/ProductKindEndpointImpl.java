package com.aromatica.modules.masterdata.catalog.productkind.endpoint.impl;

import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindDto;
import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindRequest;
import com.aromatica.modules.masterdata.catalog.productkind.endpoint.ProductKindEndpoint;
import com.aromatica.modules.masterdata.catalog.productkind.mapper.ProductKindMapper;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductKindEndpointImpl implements ProductKindEndpoint {

    ProductKindService kindService;
    ProductService productService;
    ProductKindMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<KindDto> list(boolean includeArchived) {
        return kindService.list(includeArchived).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public KindDto create(KindRequest request) {
        return mapper.toDto(kindService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional
    public KindDto update(
            String code,
            KindRequest request
    ) {
        String oldName = kindService.find(code).getName();
        KindDto dto = mapper.toDto(kindService.update(code, mapper.toEntity(request)));
        // имя формы входит в имя товара, поэтому после переименования пересобираем имена
        if (!oldName.equals(dto.name())) {
            productService.recomposeNamesForKind(code);
        }
        return dto;
    }

    @Override
    @Transactional
    public KindDto setArchived(
            String code,
            boolean archived
    ) {
        return mapper.toDto(kindService.setArchived(code, archived));
    }
}
