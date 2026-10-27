package com.aromatica.modules.masterdata.settings.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.masterdata.settings.model.AppSetting;
import com.aromatica.modules.masterdata.settings.model.PosConfig;
import com.aromatica.modules.masterdata.settings.repository.AppSettingRepository;
import com.aromatica.modules.masterdata.settings.service.SettingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingServiceImpl implements SettingService {

    /** Ключи, которые можно менять через API. Новые ключи через API не заводятся. */
    private static final Set<String> KNOWN_KEYS = Set.of(
            RESERVATION_DEFAULT_DAYS, RESERVATION_MAX_DAYS,
            MAX_SELLER_DISCOUNT_PCT, DEFAULT_DILUTION_PCT);

    AppSettingRepository repository;

    @Override
    public String get(
            String key,
            String defaultValue
    ) {
        return repository.findById(key).map(AppSetting::getValue).orElse(defaultValue);
    }

    @Override
    public int getInt(
            String key,
            int defaultValue
    ) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    @Override
    public void set(
            String key,
            String value
    ) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        repository.save(setting);
    }

    @Override
    public PosConfig posConfig() {
        return new PosConfig(
                getInt(RESERVATION_DEFAULT_DAYS, 3),
                getInt(RESERVATION_MAX_DAYS, 14),
                getInt(MAX_SELLER_DISCOUNT_PCT, 20),
                getInt(DEFAULT_DILUTION_PCT, 50));
    }

    @Transactional
    @Override
    public void update(
            String key,
            String rawValue
    ) {
        if (key == null || !KNOWN_KEYS.contains(key)) {
            throw new BusinessRuleException("Неизвестная настройка: " + key);
        }
        int value;
        try {
            value = Integer.parseInt(rawValue == null ? "" : rawValue.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Значение настройки должно быть целым числом");
        }
        // концентрацию ниже 30 % магазин не наливает: это уже не парфюм
        boolean isDiscount = MAX_SELLER_DISCOUNT_PCT.equals(key);
        boolean isDilution = DEFAULT_DILUTION_PCT.equals(key);
        int min = isDiscount ? 0 : isDilution ? 30 : 1;
        int max = isDiscount || isDilution ? 100 : 60;
        if (value < min || value > max) {
            throw new BusinessRuleException(
                    "Значение настройки — от " + min + " до " + max);
        }
        set(key, String.valueOf(value));
    }
}
