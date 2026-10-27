package com.aromatica.modules.pos.storefront.endpoint;

import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosGiftRule;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosShelf;

import java.util.List;
import java.util.UUID;

public interface StorefrontEndpoint {

    PosShelf shelf(UUID warehouseId);

    List<PosProduct> searchProducts(String search, UUID warehouseId);

    List<PosProduct> packaging(UUID warehouseId);

    List<PosProduct> productCards(List<UUID> ids, UUID warehouseId);

    List<PosGiftRule> giftRules(UUID warehouseId);

    List<PosProduct> atomizers(UUID warehouseId);

    List<PosProduct> giftKindProducts(String kind, UUID warehouseId);
}
