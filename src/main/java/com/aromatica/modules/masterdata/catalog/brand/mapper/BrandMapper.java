package com.aromatica.modules.masterdata.catalog.brand.mapper;

import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandDto;
import com.aromatica.modules.masterdata.catalog.brand.dtos.BrandDtos.BrandRequest;
import com.aromatica.modules.masterdata.catalog.brand.model.Brand;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper
public interface BrandMapper {

    BrandDto toDto(Brand brand);

    /** Только то, что задаёт пользователь; id, архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    Brand toEntity(BrandRequest request);
}
