package com.aromatica.modules.pos.shift.endpoint;

import com.aromatica.modules.pos.shift.dtos.ShiftDtos.CloseShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.OpenShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftDto;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftListResponse;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.WarehousePick;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface ShiftEndpoint {

    /** Открытая смена текущего пользователя: 200 со сменой или 204, если её нет. */
    ResponseEntity<ShiftDto> currentShift();

    ShiftDto openShift(OpenShiftRequest request);

    ShiftDto closeShift(UUID shiftId, CloseShiftRequest request);

    ShiftListResponse searchShifts(ShiftSearchRequest request);

    List<ShiftDto> openShifts();

    ShiftSummary shiftSummary(UUID shiftId);

    List<WarehousePick> warehouses();
}
