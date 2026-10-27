package com.aromatica.modules.masterdata.catalog.product.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.util.CostMath;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.model.ProductNaming;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.product.service.ReadyProductService;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReadyProductServiceImpl implements ReadyProductService {

    ProductService productService;
    ProductModelService modelService;
    ProductKindService kindService;

    @Transactional
    @Override
    public CatalogProduct findOrCreate(List<Part> parts) {
        if (parts.isEmpty()) {
            throw new BusinessRuleException("Готовое без состава не бывает");
        }
        Map<UUID, BigDecimal> merged = new HashMap<>();
        parts.forEach(p -> merged.merge(p.productId(), p.qty(), BigDecimal::add));
        String key = compositionKey(merged);
        return productService.findByCompositionKey(key).orElseGet(() -> create(merged, key));
    }

    private CatalogProduct create(
            Map<UUID, BigDecimal> parts,
            String key
    ) {
        Map<UUID, CatalogProduct> products = componentsOf(parts);
        CatalogProduct liquid = requirePouredIntoAtomizer(products);

        CatalogProduct ready = new CatalogProduct();
        ready.setId(UUID.randomUUID());
        ready.setArticle(productService.nextArticle());
        ready.setKind(CatalogProduct.Kind.READY.name());
        ready.setUnit(CatalogProduct.Unit.PCS);
        ready.setModelId(liquid.getModelId());
        ready.setLabel(label(parts, products, liquid));
        ready.setName(name(liquid, ready.getLabel()));
        ready.setPriceMinor(price(parts, products));
        ready.setCompositionKey(key);
        CatalogProduct saved = productService.save(ready);
        productService.replaceComponents(saved.getId(), components(parts));
        return saved;
    }

    /** Составляющие готового: все известны и сами без состава. */
    private Map<UUID, CatalogProduct> componentsOf(Map<UUID, BigDecimal> parts) {
        Map<UUID, CatalogProduct> products = productService.findAllById(parts.keySet()).stream()
                .collect(Collectors.toMap(CatalogProduct::getId, p -> p));
        if (products.size() != parts.size()) {
            throw new BusinessRuleException("В составе готового есть неизвестный товар");
        }
        if (products.values().stream().anyMatch(CatalogProduct::isComposite)) {
            throw new BusinessRuleException("Готовое не вкладывается: составляющая сама с составом");
        }
        return products;
    }

    /**
     * Налитое — масло или распив, его аромат и есть аромат вещи (у воды и атомайзера аромата нет).
     * Налитое существует только во флаконе: без атомайзера класть на полку нечего.
     */
    private static CatalogProduct requirePouredIntoAtomizer(Map<UUID, CatalogProduct> products) {
        CatalogProduct liquid = products.values().stream()
                .filter(p -> p.hasKind(CatalogProduct.Kind.BULK) || p.hasKind(CatalogProduct.Kind.SPLIT))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Готовое — это налитое: в составе должно быть масло или распив"));
        if (products.values().stream().noneMatch(p -> p.hasKind(CatalogProduct.Kind.ATOMIZER))) {
            throw new BusinessRuleException(
                    "Без атомайзера на полку не положить — налитое существует только во флаконе");
        }
        return liquid;
    }

    private String name(
            CatalogProduct liquid,
            String label
    ) {
        ProductModel aroma = liquid.getModelId() != null ? modelService.get(liquid.getModelId()) : null;
        return ProductNaming.compose(
                aroma != null ? aroma.getBrand() : null,
                aroma != null ? aroma.getName() : liquid.getName(),
                kindService.find(CatalogProduct.Kind.READY.name()).getName(),
                label, null);
    }

    /** Цена — сумма текущих цен составляющих; администратор может поправить её в карточке. */
    private static long price(
            Map<UUID, BigDecimal> parts,
            Map<UUID, CatalogProduct> products
    ) {
        long price = 0;
        for (var e : parts.entrySet()) {
            Long unit = products.get(e.getKey()).getPriceMinor();
            price += unit != null ? CostMath.grossMinor(e.getValue(), unit) : 0;
        }
        return price;
    }

    private static List<ProductComponent> components(Map<UUID, BigDecimal> parts) {
        return parts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    ProductComponent c = new ProductComponent();
                    c.setComponentId(e.getKey());
                    c.setQty(e.getValue());
                    return c;
                })
                .toList();
    }

    /** «разлив 10 мл · 50 %» или «распив 5 мл»: то, что получил клиент. */
    private static String label(
            Map<UUID, BigDecimal> parts,
            Map<UUID, CatalogProduct> products,
            CatalogProduct liquid
    ) {
        BigDecimal liquidMl = parts.get(liquid.getId());
        if (liquid.hasKind(CatalogProduct.Kind.SPLIT)) {
            return "распив " + ProductNaming.formatVolume(liquidMl) + " мл";
        }
        BigDecimal water = parts.entrySet().stream()
                .filter(e -> products.get(e.getKey()).hasKind(CatalogProduct.Kind.BASE))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spray = liquidMl.add(water);
        BigDecimal pct = spray.signum() == 0 ? BigDecimal.valueOf(100)
                : liquidMl.multiply(BigDecimal.valueOf(100)).divide(spray, 0, RoundingMode.HALF_UP);
        return "разлив " + ProductNaming.formatVolume(spray) + " мл · "
                + pct.stripTrailingZeros().toPlainString() + " %";
    }

    /** Ключ состава: составляющие по id с количеством без хвостовых нулей. */
    static String compositionKey(Map<UUID, BigDecimal> parts) {
        return parts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> e.getKey() + ":" + e.getValue().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("|"));
    }
}
