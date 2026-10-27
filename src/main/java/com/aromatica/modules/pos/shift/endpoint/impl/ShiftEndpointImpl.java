package com.aromatica.modules.pos.shift.endpoint.impl;

import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.CloseShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.OpenShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftDto;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftListResponse;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.WarehousePick;
import com.aromatica.modules.pos.shift.endpoint.ShiftEndpoint;
import com.aromatica.modules.pos.shift.mapper.ShiftMapper;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.modules.pos.shift.service.ShiftSearchService;
import com.aromatica.modules.pos.shift.service.ShiftSummaryQueryService;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftEndpointImpl implements ShiftEndpoint {

    PosShiftService shiftService;
    ShiftSummaryQueryService summaryQuery;
    WarehouseService warehouseService;
    UserDirectory users;
    ShiftMapper shiftMapper;
    ShiftSearchService shiftSearchService;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<ShiftDto> currentShift() {
        return shiftService.currentShift(CurrentUser.login())
                .map(shiftMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    @Transactional
    public ShiftDto openShift(OpenShiftRequest request) {
        requireWarehouseAccess(request.warehouseId());
        return shiftMapper.toDto(shiftService.openShift(shiftMapper.toOpening(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public ShiftDto closeShift(
            UUID shiftId,
            CloseShiftRequest request
    ) {
        return shiftMapper.toDto(shiftService.closeShift(shiftId, shiftMapper.toClosing(request), CurrentUser.login()));
    }

    /** Рядовому продавцу чужие смены с деньгами не показываем. */
    @Override
    @Transactional(readOnly = true)
    public ShiftListResponse searchShifts(ShiftSearchRequest request) {
        AppUser user = requireUser();
        if (user.getRole() != AppUser.Role.ADMIN && user.getRole() != AppUser.Role.SENIOR_SELLER) {
            throw new AccessDeniedException("Доступно администратору и старшему продавцу");
        }
        return shiftMapper.toListResponse(shiftSearchService.search(request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftDto> openShifts() {
        return shiftMapper.toDtos(shiftService.openShifts(CurrentUser.login()));
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftSummary shiftSummary(UUID shiftId) {
        return summaryQuery.of(shiftService.requireAccessibleShift(shiftId, CurrentUser.login()));
    }

    /** Админу все склады, продавцу только назначенные. */
    @Override
    @Transactional(readOnly = true)
    public List<WarehousePick> warehouses() {
        AppUser user = requireUser();
        return warehouseService.listActive().stream()
                .filter(w -> user.canAccessWarehouse(w.getId()))
                .sorted(Comparator.comparing(Warehouse::getName, String.CASE_INSENSITIVE_ORDER))
                .map(shiftMapper::toPick)
                .toList();
    }

    /** Продавец открывает смену только на своём складе, админ — на любом. */
    private void requireWarehouseAccess(UUID warehouseId) {
        if (!requireUser().canAccessWarehouse(warehouseId)) {
            throw new AccessDeniedException("Нет доступа к этому складу — обратитесь к администратору");
        }
    }

    private AppUser requireUser() {
        return users.require(CurrentUser.login());
    }
}
