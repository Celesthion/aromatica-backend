package com.aromatica.modules.masterdata.catalog.product.mapper;

import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ComponentDto;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ComponentRequest;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductDto;
import com.aromatica.modules.masterdata.catalog.product.dtos.ProductDtos.ProductRequest;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.ProductComponent;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelImageService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Продукт, его фото и составляющие набора лежат в других таблицах: для списка
 * грузим их пачкой, 3–5 запросов на страницу, а не N+1.
 */
@Mapper
public abstract class CatalogProductMapper {

    // абстрактный маппер MapStruct создаёт сам, поэтому внедрение — полями
    @Autowired
    protected ProductService productService;
    @Autowired
    protected ProductModelService modelService;
    @Autowired
    protected ProductModelImageService imageService;

    public ProductDto toDto(CatalogProduct product) {
        return toDtos(List.of(product)).getFirst();
    }

    public List<ProductDto> toDtos(List<CatalogProduct> products) {
        Map<UUID, ProductModel> models = new HashMap<>();
        modelService.findAllById(products.stream()
                        .map(CatalogProduct::getModelId).filter(Objects::nonNull).distinct().toList())
                .forEach(m -> models.put(m.getId(), m));
        // своего фото у товара нет, показываем основное фото продукта
        Map<UUID, UUID> mainImages = imageService.mainImageIds(models.keySet());
        Map<UUID, List<ComponentDto>> components = components(products);
        return products.stream().map(p -> {
            ProductModel model = p.getModelId() != null ? models.get(p.getModelId()) : null;
            return toDto(p,
                    model != null ? model.getBrand() : null,
                    model != null ? model.getName() : null,
                    model != null ? mainImages.get(model.getId()) : null,
                    components.getOrDefault(p.getId(), List.of()));
        }).toList();
    }

    // в API цена называется priceKopecks — так её читает фронт; в схеме и модели — *Minor, как все суммы
    @Mapping(target = "priceKopecks", source = "product.priceMinor")
    protected abstract ProductDto toDto(CatalogProduct product, String modelBrand, String modelName,
                                        UUID mainImageId, List<ComponentDto> components);

    /**
     * Только то, что задаёт пользователь; остальное, включая имя, ставит сервис.
     * Не присланные форма и единица остаются null — сервис возьмёт их по форме.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR,
            ignoreUnmappedSourceProperties = "components")
    @Mapping(target = "priceMinor", source = "priceKopecks")
    public abstract CatalogProduct toEntity(ProductRequest request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    @Mapping(target = "componentId", source = "productId")
    public abstract ProductComponent toComponent(ComponentRequest request);

    public abstract List<ProductComponent> toComponents(List<ComponentRequest> requests);

    private Map<UUID, List<ComponentDto>> components(List<CatalogProduct> products) {
        List<UUID> compositeIds = products.stream()
                .filter(CatalogProduct::isComposite).map(CatalogProduct::getId).toList();
        if (compositeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ProductComponent>> raw = productService.componentsOf(compositeIds);
        Map<UUID, CatalogProduct> parts = productService.findAllById(raw.values().stream()
                        .flatMap(List::stream).map(ProductComponent::getComponentId).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogProduct::getId, x -> x));
        Map<UUID, List<ComponentDto>> out = new HashMap<>();
        raw.forEach((productId, lines) -> out.put(productId, lines.stream()
                .map(c -> toComponentDto(c, parts.get(c.getComponentId())))
                .toList()));
        return out;
    }

    /** Составляющей может не оказаться в каталоге: тогда вместо имени её id. */
    private static ComponentDto toComponentDto(
            ProductComponent component,
            @Nullable CatalogProduct part
    ) {
        return new ComponentDto(component.getComponentId(),
                part != null ? part.getName() : component.getComponentId().toString(), component.getQty(),
                part != null ? part.getUnit() : null,
                part != null ? part.getKind() : null);
    }
}
