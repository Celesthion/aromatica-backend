package com.aromatica.modules.inventory.convert.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.inventory.convert.model.ConvertOpening;
import com.aromatica.modules.inventory.convert.model.SplitOption;
import com.aromatica.modules.inventory.convert.service.ConvertService;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModel;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import com.aromatica.security.auth.authentication.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConvertServiceImpl implements ConvertService {

    ProductService productService;
    ProductModelService modelService;
    DocService docService;
    PostingService postingService;
    DocEventService docEventService;
    Clock clock;

    @Override
    public SplitOption splitInfo(UUID sourceProductId) {
        CatalogProduct source = productService.find(sourceProductId);
        if (source.getModelId() == null) {
            return new SplitOption(null, null);
        }
        var existing = productService.findByModelId(source.getModelId()).stream()
                .filter(p -> p.hasKind(CatalogProduct.Kind.SPLIT) && !p.isArchived())
                .findFirst();
        if (existing.isPresent()) {
            return new SplitOption(existing.get(), existing.get().getName());
        }
        ProductModel aroma = modelService.get(source.getModelId());
        String suggested = aroma != null
                ? splitName(aroma)
                : source.getName() + " (распив)";
        return new SplitOption(null, suggested);
    }

    @Transactional
    @Override
    public StockDoc open(ConvertOpening opening) {
        CatalogProduct source = productService.find(opening.sourceProductId());
        if (source.getUnit() != CatalogProduct.Unit.PCS) {
            throw new BusinessRuleException("Вскрывать можно только штучную позицию (флакон или тестер)");
        }
        BigDecimal pcs = opening.sourcePcs(); // умолчание и знак проверены на входе (record + Bean Validation)

        BigDecimal targetMl = opening.targetMl();
        if (targetMl == null) {
            if (source.getVolumeMl() == null) {
                throw new BusinessRuleException(
                        "У позиции не указан объём (мл) — укажите объём вскрытия явно");
            }
            targetMl = source.getVolumeMl().multiply(pcs);
        }
        if (targetMl.signum() <= 0) {
            throw new BusinessRuleException("Объём распива должен быть больше нуля");
        }

        CatalogProduct target = findOrCreateSplitSku(source, opening.targetPriceMinor(), opening.targetName());

        StockDoc draft = StockDoc.draft(DocType.CONVERT, LocalDateTime.now(clock), opening.warehouseId(),
                opening.comment() != null ? opening.comment() : "Вскрытие: " + source.getName(),
                List.of(StockDocLine.of(source.getId(), pcs, 0, 0),
                        StockDocLine.of(target.getId(), targetMl, 0, 0)));
        UUID docId = docService.create(draft).getId();
        StockDoc posted = postingService.post(docId);
        // как у остальных документов: вскрытие видно в истории документа и в ленте
        docEventService.record(docId, DocEvent.Action.POST, CurrentUser.login());
        return posted;
    }

    /** Имя распив-SKU; бренд может отсутствовать (служебные продукты). */
    private static String splitName(ProductModel aroma) {
        return (aroma.getBrand() != null ? aroma.getBrand() + " - " : "")
                + aroma.getName() + " (распив)";
    }

    /** Создаётся при первом вскрытии. */
    private CatalogProduct findOrCreateSplitSku(
            CatalogProduct source,
            Long priceMinor,
            String name
    ) {
        if (source.getModelId() == null) {
            throw new BusinessRuleException(
                    "Позиция не привязана к аромату — сначала укажите аромат в карточке товара");
        }
        return productService.findByModelId(source.getModelId()).stream()
                .filter(p -> p.hasKind(CatalogProduct.Kind.SPLIT) && !p.isArchived())
                .findFirst()
                .orElseGet(() -> {
                    ProductModel aroma = modelService.get(source.getModelId());
                    if (aroma == null) {
                        throw new NotFoundException("Аромат не найден");
                    }
                    CatalogProduct split = new CatalogProduct();
                    split.setId(UUID.randomUUID());
                    split.setName(name != null && !name.isBlank() ? name.trim()
                            : splitName(aroma));
                    split.setModelId(aroma.getId());
                    split.setKind(CatalogProduct.Kind.SPLIT.name());
                    split.setUnit(CatalogProduct.Unit.ML);
                    split.setPriceMinor(priceMinor);
                    return productService.save(split);
                });
    }
}
