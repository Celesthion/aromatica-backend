package com.aromatica.modules.masterdata.settings.model;

/** Настройки кассы для терминала. Сроки брони — в днях, скидка и доля масла — в %. */
public record PosConfig(
        int reservationDefaultDays,
        int reservationMaxDays,
        int maxSellerDiscountPct,
        int defaultDilutionPct
) {
}
