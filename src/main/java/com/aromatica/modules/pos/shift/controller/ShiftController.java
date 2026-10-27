package com.aromatica.modules.pos.shift.controller;

import com.aromatica.modules.pos.shift.dtos.ShiftDtos.CloseShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.OpenShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftDto;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftListResponse;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.WarehousePick;
import com.aromatica.modules.pos.shift.endpoint.ShiftEndpoint;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: смены", description = "Открытие и закрытие смены, сводка и журнал. /api/admin/** продавцу закрыт; отдельные методы сужают доступ своими ролями.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftController {

    ShiftEndpoint shiftEndpoint;

    @Operation(summary = "Текущая смена кассира", description = "Ищет среди доступных кассиру складов. 204 — касса закрыта.")
    @GetMapping("/shift/current")
    public ResponseEntity<ShiftDto> currentShift() {
        return shiftEndpoint.currentShift();
    }

    @Operation(summary = "Открыть смену", description = "Только администратор или старший продавец.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SENIOR_SELLER')")
    @PostMapping("/shift/open")
    public ShiftDto openShift(@Valid @RequestBody OpenShiftRequest request) {
        return shiftEndpoint.openShift(request);
    }

    @Operation(summary = "Закрыть смену")
    @PostMapping("/shifts/{id}/close")
    public ShiftDto closeShift(
            @PathVariable UUID id,
            @Valid @RequestBody CloseShiftRequest request
    ) {
        return shiftEndpoint.closeShift(id, request);
    }

    @Operation(summary = "Поиск смен", description = "Только администратор: в выдаче выручка, расхождения по кассе и чужие смены.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/shifts/search")
    public ShiftListResponse searchShifts(@RequestBody ShiftSearchRequest request) {
        return shiftEndpoint.searchShifts(request);
    }

    @Operation(summary = "Открытые смены", description = "Если открытых смен несколько, терминал предлагает выбрать. Не-администратор видит только смены своих складов.")
    @GetMapping("/shifts/open")
    public List<ShiftDto> openShifts() {
        return shiftEndpoint.openShifts();
    }

    @Operation(summary = "Сводка по смене")
    @GetMapping("/shifts/{id}/summary")
    public ShiftSummary shiftSummary(@PathVariable UUID id) {
        return shiftEndpoint.shiftSummary(id);
    }

    @Operation(summary = "Склады для открытия смены", description = "Администратору все, продавцу только назначенные.")
    @GetMapping("/warehouses")
    public List<WarehousePick> warehouses() {
        return shiftEndpoint.warehouses();
    }
}
