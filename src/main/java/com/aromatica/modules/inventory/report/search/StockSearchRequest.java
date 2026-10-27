package com.aromatica.modules.inventory.report.search;

import com.aromatica.common.search.BaseSearchRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockSearchRequest extends BaseSearchRequest<StockSort> {

    /** Подстрока названия позиции. */
    String q;
    /** null — склад по умолчанию. */
    UUID warehouseId;
    /** Только эти позиции — например, строки, которые уже показаны в списке товаров. */
    List<UUID> productIds;

    @Override
    protected StockSort getDefaultSortBy() {
        return StockSort.NAME;
    }
}
