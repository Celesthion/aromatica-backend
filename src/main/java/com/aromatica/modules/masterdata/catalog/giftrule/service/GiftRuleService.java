package com.aromatica.modules.masterdata.catalog.giftrule.service;

import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;

import java.util.List;
import java.util.UUID;

/** Товары и формы проверяет через их сервисы, а не через чужие репозитории. */
public interface GiftRuleService {

    List<GiftRule> list();

    List<GiftRule> activeRules();

    GiftRule create(GiftRule rule);

    GiftRule update(UUID id, GiftRule changes);

    GiftRule toggleActive(UUID id);

    void delete(UUID id);
}
