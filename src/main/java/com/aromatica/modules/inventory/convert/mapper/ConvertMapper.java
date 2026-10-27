package com.aromatica.modules.inventory.convert.mapper;

import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.ConvertRequest;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.convert.model.ConvertOpening;
import com.aromatica.modules.inventory.convert.model.SplitOption;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import org.mapstruct.Mapper;

@Mapper
public interface ConvertMapper {

    ConvertOpening toCommand(ConvertRequest request);

    default SplitInfo toSplitInfo(SplitOption option) {
        CatalogProduct split = option.existing();
        return split == null
                ? new SplitInfo(false, null, null, null, option.suggestedName())
                : new SplitInfo(true, split.getId(), split.getName(), split.getPriceMinor(), option.suggestedName());
    }
}
