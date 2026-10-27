package com.aromatica.modules.inventory.convert.model;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import org.jspecify.annotations.Nullable;

/**
 * Распив аромата перед вскрытием: уже заведённый SKU или имя, которое предложить для нового.
 *
 * @param existing      неархивный распив этого аромата; null — его ещё нет
 * @param suggestedName имя для нового распива; null — товар не привязан к аромату
 */
public record SplitOption(

        @Nullable
        CatalogProduct existing,

        @Nullable
        String suggestedName
) {

    public boolean exists() {
        return existing != null;
    }
}
