package com.aromatica.modules.pos.reservation.endpoint;

import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.CreateReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ExtendReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationDto;

import java.util.List;
import java.util.UUID;

public interface ReservationEndpoint {

    List<ReservationDto> list(UUID warehouseId);

    ReservationDto create(CreateReservationRequest request);

    void cancel(UUID id);

    ReservationDto extend(UUID id, ExtendReservationRequest request);
}
