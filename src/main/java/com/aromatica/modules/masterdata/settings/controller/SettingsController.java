package com.aromatica.modules.masterdata.settings.controller;

import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.PosSettings;
import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.UpdateSettingRequest;
import com.aromatica.modules.masterdata.settings.endpoint.SettingsEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Настройки", description = "Читает касса (роли как у /api/pos/**), правит только администратор через /api/admin.")
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingsController {

    SettingsEndpoint settingsEndpoint;

    @Operation(summary = "Настройки кассы", description = "Сейчас это сроки брони.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
    @GetMapping("/api/pos/settings")
    public PosSettings posSettings() {
        return settingsEndpoint.posSettings();
    }

    @Operation(summary = "Изменить настройку", description = "Только администратор. Принимает {key, value}: только известные ключи, значение — целое число в допустимых для ключа пределах.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/admin/settings")
    public PosSettings update(@Valid @RequestBody UpdateSettingRequest request) {
        return settingsEndpoint.update(request);
    }
}
