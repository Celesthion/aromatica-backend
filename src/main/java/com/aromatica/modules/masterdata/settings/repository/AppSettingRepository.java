package com.aromatica.modules.masterdata.settings.repository;

import com.aromatica.common.persistence.EntityRepository;
import com.aromatica.modules.masterdata.settings.model.AppSetting;

public interface AppSettingRepository extends EntityRepository<AppSetting, String> {
}
