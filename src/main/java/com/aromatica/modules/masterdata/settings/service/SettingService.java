package com.aromatica.modules.masterdata.settings.service;

import com.aromatica.modules.masterdata.settings.model.PosConfig;

/** Единственная точка доступа к AppSettingRepository. Значения хранятся строками, тип приводит вызывающий. */
public interface SettingService {

    /** Срок брони по умолчанию в днях, если касса не указала свой. */
    String RESERVATION_DEFAULT_DAYS = "reservation.default_days";

    /** Максимальный срок брони в днях, и при создании, и при продлении. */
    String RESERVATION_MAX_DAYS = "reservation.max_days";

    /** Максимальная скидка продавца на строку чека, %. У админа потолка нет. */
    String MAX_SELLER_DISCOUNT_PCT = "pos.max_seller_discount_pct";

    /** Доля масла в разливе по умолчанию, %; продавец правит её в каждой продаже. */
    String DEFAULT_DILUTION_PCT = "pos.default_dilution_pct";

    /** Значение настройки; defaultValue — если ключа нет. */
    String get(String key, String defaultValue);

    /** Целое значение; defaultValue — если ключа нет или там не число. */
    int getInt(String key, int defaultValue);

    void set(String key, String value);

    /** Настройки кассы с умолчаниями для отсутствующих ключей. */
    PosConfig posConfig();

    /** Только известные ключи и целое в допустимых пределах, иначе 400. */
    void update(String key, String rawValue);
}
