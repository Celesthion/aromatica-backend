package com.aromatica.modules.masterdata.settings.mapper;

import com.aromatica.modules.masterdata.settings.dtos.SettingsDtos.PosSettings;
import com.aromatica.modules.masterdata.settings.model.PosConfig;
import org.mapstruct.Mapper;

@Mapper
public interface SettingsMapper {

    PosSettings toDto(PosConfig config);
}
