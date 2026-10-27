package com.aromatica.modules.masterdata.catalog.productmodel.mapper;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ImageDto;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ModelRow;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelDto;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ProductModelRequest;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.SkuDto;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelImageService;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Формы (SKU, счётчик) и фото лежат в других таблицах: для списка грузим их
 * пачкой на страницу, без N+1.
 */
@Mapper
public abstract class ProductModelMapper {

    // абстрактный маппер MapStruct создаёт сам, поэтому внедрение — полями
    @Autowired
    protected ProductService productService;
    @Autowired
    protected ProductModelImageService imageService;
    @Autowired
    protected ProductModelImageMapper imageMapper;

    public List<ModelRow> toRows(List<ProductModel> models) {
        List<UUID> ids = models.stream().map(ProductModel::getId).toList();
        Map<UUID, Long> formCounts = productService.countByModelIds(ids);
        Map<UUID, UUID> mainImages = imageService.mainImageIds(ids);
        return models.stream()
                .map(m -> toRow(m, formCounts.getOrDefault(m.getId(), 0L), mainImages.get(m.getId())))
                .toList();
    }

    /** Полная карточка со SKU и фото для быстрого просмотра. */
    public ProductModelDto toDto(ProductModel model) {
        return toDto(model,
                toSkus(productService.findByModelId(model.getId())),
                imageService.listOf(model.getId()).stream().map(imageMapper::toDto).toList());
    }

    protected abstract ModelRow toRow(ProductModel model, long forms, UUID mainImageId);

    protected abstract ProductModelDto toDto(ProductModel model, List<SkuDto> skus, List<ImageDto> images);

    /** Только то, что задаёт пользователь; id, архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    public abstract ProductModel toEntity(ProductModelRequest request);

    protected abstract SkuDto toSku(CatalogProduct product);

    protected abstract List<SkuDto> toSkus(List<CatalogProduct> products);
}
