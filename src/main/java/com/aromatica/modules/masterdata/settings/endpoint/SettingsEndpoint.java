package com.aromatica.modules.masterdata.settings.endpoint;

import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.PosSettings;
import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.UpdateSettingRequest;

public interface SettingsEndpoint {

    PosSettings posSettings();

    /** Меняет одну настройку: только известные ключи, значение проверяется по ключу. */
    PosSettings update(UpdateSettingRequest request);
}
