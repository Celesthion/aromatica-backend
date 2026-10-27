package com.aromatica.modules.pos.customer.endpoint.impl;

import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import com.aromatica.modules.masterdata.party.service.CounterpartySearchService;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerDto;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerRequest;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PurchaseDto;
import com.aromatica.modules.pos.customer.endpoint.PosCustomerEndpoint;
import com.aromatica.modules.pos.customer.mapper.PosCustomerMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosCustomerEndpointImpl implements PosCustomerEndpoint {
    PosCustomerMapper mapper;
    /** Справочник целиком касса не показывает: кассир должен знать имя или номер. */
    private static final int MIN_QUERY = 3;

    /** У прилавка длинный список бесполезен. */
    private static final int SEARCH_LIMIT = 20;

    private static final int HISTORY_LIMIT = 20;

    CounterpartyService counterpartyService;
    CounterpartySearchService counterpartySearchService;
    DocService docService;
    ProductService productService;

    @Override
    @Transactional(readOnly = true)
    public List<PosCustomerDto> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY) {
            return List.of();
        }
        CounterpartySearchRequest request = new CounterpartySearchRequest();
        request.setQ(q);
        request.setType(Counterparty.Type.CUSTOMER);
        request.setSize(SEARCH_LIMIT);
        return counterpartySearchService.searchTop(request).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public PosCustomerDto create(PosCustomerRequest request) {
        return mapper.toDto(counterpartyService.create(mapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDto> purchases(UUID customerId) {
        var docs = docService.lastSalesOf(customerId, HISTORY_LIMIT);

        // товары одним запросом на всю историю; строки чеков уже подняты с документами
        Set<UUID> productIds = docs.stream()
                .flatMap(d -> d.getLines().stream())
                .map(StockDocLine::getProductId)
                .collect(Collectors.toSet());
        Map<UUID, CatalogProduct> products = productService.findAllById(productIds).stream()
                .collect(Collectors.toMap(CatalogProduct::getId, p -> p));

        return docs.stream().map(d -> mapper.toPurchase(d, products)).toList();
    }
}
