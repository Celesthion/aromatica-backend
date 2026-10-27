package com.aromatica.modules.masterdata.catalog.product.service;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Готовое (READY) — налитое вместе с атомайзером; карточку заводит система, когда налитое
 * кладут на полку. См. docs/KITS-SHELF.md.
 */
public interface ReadyProductService {

    /** Составляющая готового: товар и сколько его внутри одной вещи. */
    record Part(
            UUID productId,
            BigDecimal qty
    ) {}

    /** Одинаковый состав — одна карточка («2 шт»). Новая получает цену — сумму текущих цен составляющих. */
    CatalogProduct findOrCreate(List<Part> parts);
}
