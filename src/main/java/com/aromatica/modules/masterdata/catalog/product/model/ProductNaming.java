package com.aromatica.modules.masterdata.catalog.product.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Полное имя «Бренд — Модель · Форма · [Отличие] · [Объём]», хранится готовым в {@code product.name}.
 * SQL-зеркало формулы — CatalogProductRepository.recomposeNames, менять синхронно.
 */
public final class ProductNaming {

    private ProductNaming() {}

    public static String compose(
            String brand,
            String modelName,
            String kindName,
            String label,
            BigDecimal volumeMl
    ) {
        List<String> parts = new ArrayList<>();
        String model = notBlank(brand) ? brand.trim() + " — " + safe(modelName) : safe(modelName);
        add(parts, model);
        add(parts, kindName);
        add(parts, label);
        if (volumeMl != null) {
            add(parts, formatVolume(volumeMl) + " мл");
        }
        return String.join(" · ", parts);
    }

    /** «10.000» → «10», «0.500» → «0.5». */
    public static String formatVolume(BigDecimal volumeMl) {
        return volumeMl.stripTrailingZeros().toPlainString();
    }

    private static void add(
            List<String> parts,
            String s
    ) {
        if (notBlank(s)) {
            parts.add(s.trim());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
