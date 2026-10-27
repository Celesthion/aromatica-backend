package com.aromatica.modules.pos.reservation.endpoint.impl;

import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.CreateReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ExtendReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationDto;
import com.aromatica.modules.pos.reservation.endpoint.ReservationEndpoint;
import com.aromatica.modules.pos.reservation.mapper.ReservationMapper;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.service.ReservationService;
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
public class ReservationEndpointImpl implements ReservationEndpoint {

    ReservationService reservationService;
    ReservationMapper reservationMapper;
    WarehouseLookup warehouseLookup;

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDto> list(UUID warehouseId) {
        UUID wh = warehouseId != null ? warehouseId : warehouseLookup.defaultWarehouseId();
        return reservationMapper.toDtos(reservationService.list(wh), wh);
    }

    @Override
    @Transactional
    public ReservationDto create(CreateReservationRequest request) {
        Reservation created = reservationService.create(reservationMapper.toEntity(request), request.days());
        return reservationMapper.toDto(created);
    }

    @Override
    @Transactional
    public void cancel(UUID id) {
        reservationService.cancel(id);
    }

    @Override
    @Transactional
    public ReservationDto extend(
            UUID id,
            ExtendReservationRequest request
    ) {
        return reservationMapper.toDto(reservationService.extend(id, request != null ? request.days() : null));
    }
}
