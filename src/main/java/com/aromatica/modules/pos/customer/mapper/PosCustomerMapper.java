package com.aromatica.modules.pos.customer.mapper;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerDto;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerRequest;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PurchaseDto;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PurchaseLineDto;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface PosCustomerMapper {

    PosCustomerDto toDto(Counterparty customer);

    /** С кассы заводят только покупателей; id, заметку, архив и аудит ставит сервис. */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    @Mapping(target = "type", constant = "CUSTOMER")
    Counterparty toEntity(PosCustomerRequest request);

    /** Покупка клиента; товары для всех строк endpoint берёт одним запросом. */
    default PurchaseDto toPurchase(StockDoc doc, Map<UUID, CatalogProduct> products) {
        List<PurchaseLineDto> lines = doc.getLines().stream()
                .sorted(Comparator.comparingInt(StockDocLine::getLineNo))
                .map(l -> toPurchaseLine(l, products.get(l.getProductId())))
                .toList();
        return new PurchaseDto(doc.getId(), doc.getNumber(), doc.getMoment(),
                doc.getSumMinor(), doc.getLines().size(), lines);
    }

    /** Товара может не оказаться в каталоге: покажем id, но строку не теряем. */
    default PurchaseLineDto toPurchaseLine(StockDocLine line, @Nullable CatalogProduct product) {
        return new PurchaseLineDto(
                product != null ? product.getName() : line.getProductId().toString(),
                product != null ? product.getUnit() : CatalogProduct.Unit.PCS,
                line.getQty(), line.sumMinor(), line.isGift());
    }
}
