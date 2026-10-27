package com.aromatica.modules.masterdata.catalog.productkind.mapper;

import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindDto;
import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindRequest;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper
public interface ProductKindMapper {

    KindDto toDto(ProductKind kind);

    /** Только то, что задаёт пользователь; код, системность, архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    ProductKind toEntity(KindRequest request);
}
