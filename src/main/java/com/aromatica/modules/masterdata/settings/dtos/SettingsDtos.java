package com.aromatica.modules.masterdata.settings.dtos;

import jakarta.validation.constraints.NotBlank;

public final class SettingsDtos {

    private SettingsDtos() {}

    /** Настройки, нужные кассе. Сроки брони — в днях. */
    public record PosSettings(

            int reservationDefaultDays,

            int reservationMaxDays,

            int maxSellerDiscountPct,

            /** Доля масла в разливе по умолчанию, %. */
            int defaultDilutionPct
    ) {}

    /** Смена одной настройки админом. value приходит строкой и проверяется по ключу. */
    public record UpdateSettingRequest(

            @NotBlank(message = "Не указан ключ настройки")
            String key,

            String value
    ) {}
}
