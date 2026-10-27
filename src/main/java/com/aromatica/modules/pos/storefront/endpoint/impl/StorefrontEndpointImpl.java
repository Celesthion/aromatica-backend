package com.aromatica.modules.pos.storefront.endpoint.impl;

import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;
import com.aromatica.modules.pos.storefront.endpoint.StorefrontEndpoint;
import com.aromatica.modules.pos.storefront.service.PosCatalogQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorefrontEndpointImpl implements StorefrontEndpoint {

    PosCatalogQueryService catalogService;

    @Override
    @Transactional(readOnly = true)
    public PosShelf shelf(UUID warehouseId) {
        return catalogService.shelf(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosProduct> searchProducts(
            String search,
            UUID warehouseId
    ) {
        return catalogService.searchProducts(search, warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosProduct> packaging(UUID warehouseId) {
        return catalogService.packaging(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosProduct> productCards(
            List<UUID> ids,
            UUID warehouseId
    ) {
        return catalogService.cardsByIds(ids, warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosGiftRule> giftRules(UUID warehouseId) {
        return catalogService.giftRules(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosProduct> atomizers(UUID warehouseId) {
        return catalogService.atomizers(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosProduct> giftKindProducts(
            String kind,
            UUID warehouseId
    ) {
        return catalogService.giftKindProducts(kind, warehouseId);
    }
}
