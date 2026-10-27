package com.aromatica.modules.masterdata.catalog.product.search;

import com.aromatica.common.search.BaseSearchRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductSearchRequest extends BaseSearchRequest<ProductSort> {

    /** Подстрока названия или артикула. */
    String q;
    /** Код формы (точное совпадение). */
    String kind;
    Boolean includeArchived;
    /** Цена от и до, в копейках, включительно. Товар без цены в диапазон не попадает. */
    Long priceMinKopecks;
    Long priceMaxKopecks;
    /** Объём от и до, в мл, включительно. Товар без объёма в диапазон не попадает. */
    BigDecimal volumeMinMl;
    BigDecimal volumeMaxMl;

    @Override
    protected ProductSort getDefaultSortBy() {
        return ProductSort.NAME;
    }
}
