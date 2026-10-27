package com.aromatica.modules.masterdata.catalog.giftrule.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftItem;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRule;
import com.aromatica.modules.masterdata.catalog.giftrule.model.GiftRuleCondition;
import com.aromatica.modules.masterdata.catalog.giftrule.repository.GiftRuleRepository;
import com.aromatica.modules.masterdata.catalog.giftrule.service.GiftRuleService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftRuleServiceImpl implements GiftRuleService {

    GiftRuleRepository repository;
    ProductService productService;
    ProductKindService kindService;

    @Override
    public List<GiftRule> list() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Override
    public List<GiftRule> activeRules() {
        return repository.findByActiveTrueOrderByCreatedAtAsc();
    }

    @Transactional
    @Override
    public GiftRule create(GiftRule rule) {
        GiftRule created = new GiftRule();
        created.setId(UUID.randomUUID());
        apply(created, rule);
        return repository.save(created);
    }

    @Transactional
    @Override
    public GiftRule update(
            UUID id,
            GiftRule changes
    ) {
        GiftRule rule = find(id);
        apply(rule, changes);
        return rule;
    }

    @Transactional
    @Override
    public GiftRule toggleActive(UUID id) {
        GiftRule rule = find(id);
        rule.setActive(!rule.isActive());
        return rule;
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private GiftRule find(UUID id) {
        return repository.require(id, "Правило подарка не найдено");
    }

    private void apply(
            GiftRule rule,
            GiftRule changes
    ) {
        if (changes.getItems().isEmpty()) {
            throw new BusinessRuleException("Добавьте хотя бы одну позицию подарка");
        }
        changes.getItems().forEach(this::requireValidItem);

        // без условий акция сработала бы на любой чек
        if (changes.getConditions().isEmpty()) {
            throw new BusinessRuleException("Добавьте хотя бы одно условие");
        }
        changes.getConditions().forEach(this::requireValidCondition);
        List<GiftRuleCondition> conditions = normalizeGroups(changes.getConditions());
        boolean hasSum = conditions.stream().anyMatch(c -> c.getType() == GiftRuleCondition.Type.SUM);
        if (changes.getStepMinor() != null && !hasSum) {
            throw new BusinessRuleException(
                    "Шаг «+1 подарок за каждые N сом» работает только с условием по сумме чека");
        }

        rule.setName(changes.getName().trim());
        rule.getConditions().clear();
        rule.getConditions().addAll(conditions);
        rule.setStepMinor(changes.getStepMinor());
        rule.setMaxGifts(changes.getMaxGifts());
        rule.getItems().clear();
        rule.getItems().addAll(changes.getItems());
    }

    private void requireValidItem(GiftItem item) {
        if (item.getQty() == null || item.getQty().signum() <= 0) {
            throw new BusinessRuleException("Количество в позиции подарка должно быть больше нуля");
        }
        boolean hasProduct = item.getProductId() != null;
        boolean hasKind = item.getKind() != null && !item.getKind().isBlank();
        if (hasProduct == hasKind) {
            throw new BusinessRuleException(
                    "Позиция подарка — либо конкретный товар, либо любой товар формы");
        }
        CatalogProduct.Unit unit;
        if (hasProduct) {
            CatalogProduct product;
            try {
                product = productService.find(item.getProductId());
            } catch (NotFoundException e) {
                throw new BusinessRuleException("Неизвестный товар в подарке: " + item.getProductId());
            }
            if (product.isArchived()) {
                throw new BusinessRuleException(
                        "Товар «" + product.getName() + "» в архиве — в подарок не годится");
            }
            unit = product.getUnit();
            item.setKind(null);
        } else {
            ProductKind kind = kindService.get(item.getKind())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Неизвестная форма в подарке: " + item.getKind()));
            if (kind.isArchived()) {
                throw new BusinessRuleException(
                        "Форма «" + kind.getName() + "» в архиве — в подарок не годится");
            }
            if (!kind.isGiftEligible()) {
                throw new BusinessRuleException(
                        "Форма «" + kind.getName() + "» выключена для подарков (см. Каталог → Формы)");
            }
            unit = kind.getUnit();
            item.setProductId(null);
        }
        // штучные позиции только целыми: полфлакона не спишешь
        if (unit == CatalogProduct.Unit.PCS && item.getQty().stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("Количество штучной позиции подарка — целое число");
        }
    }

    /** Условие несёт только поля своего типа; остальные обнуляем, чтобы в базу не легло лишнее. */
    private void requireValidCondition(GiftRuleCondition c) {
        GiftRuleCondition.Type type = c.getType();
        if (type == null) {
            throw new BusinessRuleException("Не указан тип условия");
        }
        c.setGroupNo(Math.max(0, c.getGroupNo()));
        switch (type) {
            case SUM -> {
                if (c.getMinSumMinor() == null || c.getMinSumMinor() <= 0) {
                    throw new BusinessRuleException("Укажите сумму чека больше нуля");
                }
                c.setKind(null);
                c.setMinVolumeMl(null);
                c.setProductId(null);
            }
            case KIND -> {
                if (c.getKind() == null || c.getKind().isBlank()) {
                    throw new BusinessRuleException("Выберите форму товара для условия");
                }
                if (kindService.get(c.getKind()).isEmpty()) {
                    throw new BusinessRuleException("Неизвестная форма товара: " + c.getKind());
                }
                if (c.getMinVolumeMl() != null && c.getMinVolumeMl().signum() <= 0) {
                    throw new BusinessRuleException("Мин. объём должен быть больше нуля");
                }
                c.setMinSumMinor(null);
                c.setProductId(null);
            }
            case PRODUCT -> {
                if (c.getProductId() == null) {
                    throw new BusinessRuleException("Выберите товар для условия");
                }
                if (!productService.exists(c.getProductId())) {
                    throw new BusinessRuleException("Неизвестный товар в условии: " + c.getProductId());
                }
                c.setMinSumMinor(null);
                c.setKind(null);
                c.setMinVolumeMl(null);
            }
        }
    }

    /** Группы подряд 0..n-1: фронт шлёт номера с дырами после удаления группы, а касса ждёт блоки подряд. */
    private static List<GiftRuleCondition> normalizeGroups(List<GiftRuleCondition> conditions) {
        List<Integer> groups = conditions.stream()
                .map(GiftRuleCondition::getGroupNo)
                .distinct()
                .sorted()
                .toList();
        Map<Integer, Integer> dense = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            dense.put(groups.get(i), i);
        }
        List<GiftRuleCondition> sorted = new ArrayList<>(conditions);
        sorted.sort(Comparator.comparingInt(c -> dense.get(c.getGroupNo())));
        sorted.forEach(c -> c.setGroupNo(dense.get(c.getGroupNo())));
        return sorted;
    }
}
