package com.aromatica.modules.masterdata.catalog.productmodel.search;

import com.aromatica.common.search.BaseSearchRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductModelSearchRequest extends BaseSearchRequest<ProductModelSort> {

    String q;
    Boolean includeArchived;

    @Override
    protected ProductModelSort getDefaultSortBy() {
        return ProductModelSort.BRAND;
    }
}
