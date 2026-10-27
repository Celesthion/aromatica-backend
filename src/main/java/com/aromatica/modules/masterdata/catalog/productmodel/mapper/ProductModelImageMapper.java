package com.aromatica.modules.masterdata.catalog.productmodel.mapper;

import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ImageDto;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImage;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImageSummary;
import org.mapstruct.Mapper;

@Mapper
public interface ProductModelImageMapper {

    ImageDto toDto(ProductModelImage image);
    ImageDto toDto(ProductModelImageSummary image);
}
