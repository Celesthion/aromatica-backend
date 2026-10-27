package com.aromatica.modules.pos.reservation.controller;

import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.CreateReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ExtendReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationDto;
import com.aromatica.modules.pos.reservation.endpoint.ReservationEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: брони", description = "Брони товара с кассы.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos/reservations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReservationController {

    ReservationEndpoint reservationEndpoint;

    @Operation(summary = "Брони склада", description = "Активные брони склада, включая просроченные (с признаком expired).")
    @GetMapping
    public List<ReservationDto> list(@RequestParam(required = false) UUID warehouseId) {
        return reservationEndpoint.list(warehouseId);
    }

    @Operation(summary = "Забронировать товар")
    @PostMapping
    public ReservationDto create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationEndpoint.create(request);
    }

    @Operation(summary = "Отменить бронь")
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        reservationEndpoint.cancel(id);
    }

    @Operation(summary = "Продлить бронь")
    @PostMapping("/{id}/extend")
    public ReservationDto extend(
            @PathVariable UUID id,
            @Valid @RequestBody ExtendReservationRequest request
    ) {
        return reservationEndpoint.extend(id, request);
    }
}
