package com.aromatica.modules.masterdata.catalog.brand.search;

import com.aromatica.common.search.BaseSearchRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BrandSearchRequest extends BaseSearchRequest<BrandSort> {

    String q;
    Boolean includeArchived;

    @Override
    protected BrandSort getDefaultSortBy() {
        return BrandSort.NAME;
    }
}
