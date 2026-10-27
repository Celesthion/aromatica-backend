package com.aromatica.modules.masterdata.settings.endpoint.impl;

import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.PosSettings;
import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.UpdateSettingRequest;
import com.aromatica.modules.masterdata.settings.endpoint.SettingsEndpoint;
import com.aromatica.modules.masterdata.settings.mapper.SettingsMapper;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingsEndpointImpl implements SettingsEndpoint {

    SettingService settingService;
    SettingsMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PosSettings posSettings() {
        return mapper.toDto(settingService.posConfig());
    }

    @Override
    @Transactional
    public PosSettings update(UpdateSettingRequest request) {
        settingService.update(request.key(), request.value());
        return posSettings();
    }
}
