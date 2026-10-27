package com.aromatica.modules.masterdata.catalog.product.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.model.ProductNaming;
import com.aromatica.modules.masterdata.catalog.product.service.ProductEditService;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductEditServiceImpl implements ProductEditService {

    ProductService productService;
    ProductModelService modelService;
    ProductKindService kindService;

    @Transactional
    @Override
    public CatalogProduct create(
            CatalogProduct product,
            List<ProductComponent> components
    ) {
        validate(product);
        checkUnique(product, null);
        List<ProductComponent> parts = requireValidComposition(kindOf(product), components);
        CatalogProduct p = new CatalogProduct();
        p.setId(UUID.randomUUID());
        p.setArticle(productService.nextArticle());
        apply(p, product);
        CatalogProduct saved = productService.save(p);
        productService.replaceComponents(saved.getId(), parts);
        return saved;
    }

    @Transactional
    @Override
    public CatalogProduct update(
            UUID id,
            CatalogProduct changes,
            List<ProductComponent> components
    ) {
        CatalogProduct p = productService.find(id);
        if (p.hasKind(CatalogProduct.Kind.READY)) {
            p.setPriceMinor(changes.getPriceMinor());
            return p;
        }
        validate(changes);
        checkUnique(changes, id);
        List<ProductComponent> parts = requireValidComposition(kindOf(changes), components);
        apply(p, changes);
        productService.replaceComponents(p.getId(), parts);
        return p;
    }

    @Transactional
    @Override
    public CatalogProduct setArchived(
            UUID id,
            boolean archived
    ) {
        CatalogProduct p = productService.find(id);
        if (p.isArchived() == archived) {
            return p;
        }
        // составляющую активного комплекта не архивируем, иначе комплект молча станет несобираемым
        if (archived && productService.isComponentOfActive(id)) {
            throw new ConflictException("Товар входит в состав комплекта — сначала уберите его оттуда");
        }
        // из архива — по правилам заведения: без дубля, второй основы и архивных составляющих
        if (!archived) {
            checkUnique(p.getModelId(), p.getKind(), p.getLabel(), p.getVolumeMl(), p.getId());
            requireActiveComponents(p);
        }
        p.setArchived(archived);
        return p;
    }

    /** Парфюмерную воду в комплект не кладём: её расходует разлив. */
    private List<ProductComponent> requireValidComposition(
            String kind,
            List<ProductComponent> lines
    ) {
        List<ProductComponent> requested = lines != null ? lines : List.of();
        if (!CatalogProduct.Kind.KIT.name().equals(kind)) {
            if (!requested.isEmpty()) {
                throw new BusinessRuleException("Состав бывает только у комплекта");
            }
            return List.of();
        }
        if (requested.isEmpty()) {
            throw new BusinessRuleException("Комплект без состава — добавьте хотя бы одну составляющую");
        }
        Map<UUID, CatalogProduct> byId = productService.findAllById(
                        requested.stream().map(ProductComponent::getComponentId).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogProduct::getId, x -> x));
        Map<UUID, BigDecimal> merged = new LinkedHashMap<>();
        for (ProductComponent c : requested) {
            CatalogProduct part = byId.get(c.getComponentId());
            if (part == null || part.isArchived()) {
                throw new BusinessRuleException("Составляющая не найдена или в архиве: " + c.getComponentId());
            }
            if (part.isComposite()) {
                throw new BusinessRuleException("«" + part.getName()
                        + "» сам с составом — комплект в комплект не вкладывается");
            }
            if (part.hasKind(CatalogProduct.Kind.BASE)) {
                throw new BusinessRuleException("Парфюмерная вода в комплект не кладётся");
            }
            merged.merge(part.getId(), c.getQty(), BigDecimal::add);
        }
        List<ProductComponent> components = new ArrayList<>();
        merged.forEach((id, qty) -> {
            ProductComponent pc = new ProductComponent();
            pc.setComponentId(id);
            pc.setQty(qty);
            components.add(pc);
        });
        return components;
    }

    /** Комплект возвращается из архива, только если все его составляющие на месте. */
    private void requireActiveComponents(CatalogProduct kit) {
        List<UUID> partIds = productService.componentsOf(List.of(kit.getId()))
                .getOrDefault(kit.getId(), List.of()).stream()
                .map(ProductComponent::getComponentId).toList();
        productService.findAllById(partIds).stream()
                .filter(CatalogProduct::isArchived)
                .findFirst()
                .ifPresent(part -> {
                    throw new ConflictException("«" + part.getName() + "» в архиве — сначала верните составляющую");
                });
    }

    /** Товар определяется набором (продукт, форма, объём, отличие); архивные не учитываем. */
    private void checkUnique(
            CatalogProduct product,
            UUID excludeId
    ) {
        checkUnique(product.getModelId(), kindOf(product), product.getLabel(), product.getVolumeMl(), excludeId);
    }

    /** Форма из запроса; не задана — обычный товар. */
    private static String kindOf(CatalogProduct product) {
        return product.getKind() != null ? product.getKind() : CatalogProduct.Kind.GOODS.name();
    }

    private void checkUnique(
            UUID modelId,
            String kind,
            String rawLabel,
            BigDecimal volumeMl,
            UUID excludeId
    ) {
        String label = normalizeLabel(rawLabel);
        boolean dup = modelId != null && productService.findByModelId(modelId).stream()
                .filter(x -> !x.isArchived())
                .filter(x -> excludeId == null || !x.getId().equals(excludeId))
                .anyMatch(x -> x.getKind().equals(kind)
                        && Objects.equals(normalizeLabel(x.getLabel()), label)
                        && sameVolume(x.getVolumeMl(), volumeMl));
        if (dup) {
            throw new BusinessRuleException(
                    "Такой товар уже есть (продукт, форма, объём и название совпадают)");
        }
        checkSingleBase(kind, excludeId);
    }

    /**
     * Парфюмерная вода одна на магазин: сервер дописывает её списание в чек, находя карточку
     * по форме, — со второй активной списание могло бы пойти с пустой.
     */
    private void checkSingleBase(
            String kind,
            UUID excludeId
    ) {
        if (!CatalogProduct.Kind.BASE.name().equals(kind)) {
            return;
        }
        boolean exists = productService.findActiveByKind(CatalogProduct.Kind.BASE.name()).stream()
                .anyMatch(x -> excludeId == null || !x.getId().equals(excludeId));
        if (exists) {
            throw new BusinessRuleException(
                    "Парфюмерная вода уже заведена — она в магазине одна, её расходует весь разлив");
        }
    }

    private static String normalizeLabel(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameVolume(
            BigDecimal a,
            BigDecimal b
    ) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /** Правила, где цена зависит от формы. Простые поля проверяет Bean Validation. */
    private static void validate(CatalogProduct product) {
        String kind = kindOf(product);
        if (CatalogProduct.Kind.READY.name().equals(kind)) {
            throw new BusinessRuleException("Готовое заводит сама система, когда налитое кладётся на полку");
        }
        boolean perMl = CatalogProduct.Kind.SPLIT.name().equals(kind)
                || CatalogProduct.Kind.BULK.name().equals(kind);
        // тестер не продаётся, основа расходуется бесплатно: цена не нужна
        boolean notSold = CatalogProduct.Kind.TESTER.name().equals(kind)
                || CatalogProduct.Kind.BASE.name().equals(kind);
        if (!notSold && product.getPriceMinor() == null) {
            throw new BusinessRuleException(perMl ? "Укажите цену за 1 мл" : "Укажите цену продажи");
        }
    }

    private void apply(
            CatalogProduct p,
            CatalogProduct changes
    ) {
        p.setPriceMinor(changes.getPriceMinor());
        p.setDescription(changes.getDescription() != null && !changes.getDescription().isBlank()
                ? changes.getDescription().trim() : null);
        p.setModelId(changes.getModelId());
        var kind = kindService.requireActive(kindOf(changes));
        p.setKind(kind.getCode());
        p.setUnit(changes.getUnit() != null ? changes.getUnit() : kind.getUnit());
        p.setVolumeMl(changes.getVolumeMl());
        String label = changes.getLabel() != null && !changes.getLabel().isBlank()
                ? changes.getLabel().trim() : null;
        p.setLabel(label);
        ProductModel model = modelService.get(changes.getModelId());
        p.setName(ProductNaming.compose(
                model != null ? model.getBrand() : null,
                model != null ? model.getName() : null,
                kind.getName(), label, changes.getVolumeMl()));
    }
}
