package com.aromatica.modules.pos.reservation.service.impl;

import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.storefront.service.AvailableStock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Доступный остаток для витрины: остаток склада минус активные брони. */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReservationAvailableStock implements AvailableStock {

    ReservationService reservationService;

    @Override
    public Map<UUID, BigDecimal> availableQty(
            UUID warehouseId,
            Collection<UUID> productIds
    ) {
        Map<UUID, BigDecimal> available = new HashMap<>();
        reservationService.availability(warehouseId, productIds, null)
                .forEach((id, a) -> available.put(id, a.available()));
        return available;
    }
}
