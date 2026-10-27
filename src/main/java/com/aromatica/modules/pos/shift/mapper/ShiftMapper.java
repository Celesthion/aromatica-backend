package com.aromatica.modules.pos.shift.mapper;

import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.CloseShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.OpenShiftRequest;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftDto;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftListResponse;
import com.aromatica.modules.pos.shift.dtos.ShiftDtos.WarehousePick;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.model.ShiftClosing;
import com.aromatica.modules.pos.shift.model.ShiftOpening;
import com.aromatica.security.auth.service.UserDirectory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Имена склада и кассиров лежат в других модулях: для списка грузим их пачкой на страницу. */
@Mapper
public abstract class ShiftMapper {

    // абстрактный маппер MapStruct создаёт сам, поэтому внедрение — полями
    @Autowired
    protected WarehouseService warehouseService;
    @Autowired
    protected UserDirectory users;

    public ShiftDto toDto(PosShift shift) {
        return toDtos(List.of(shift)).getFirst();
    }

    /** Страница журнала смен. */
    public ShiftListResponse toListResponse(Page<PosShift> page) {
        return new ShiftListResponse(toDtos(page.getContent()), page.getNumber(), page.getTotalPages(),
                page.getTotalElements());
    }

    public List<ShiftDto> toDtos(List<PosShift> shifts) {
        Map<UUID, String> warehouses = warehouseService.names(shifts.stream().map(PosShift::getWarehouseId).toList());
        Map<UUID, String> people = users.names(shifts.stream()
                .flatMap(shift -> Stream.of(shift.getOpenedBy(), shift.getClosedBy())).toList());
        return shifts.stream()
                .map(shift -> toDto(shift, warehouses.get(shift.getWarehouseId()),
                        people.get(shift.getOpenedBy()), people.get(shift.getClosedBy())))
                .toList();
    }

    @Mapping(target = "warehouseName", source = "warehouseName")
    @Mapping(target = "openedBy", source = "openedByName")
    @Mapping(target = "closedBy", source = "closedByName")
    protected abstract ShiftDto toDto(PosShift shift, String warehouseName, String openedByName, String closedByName);

    @Mapping(target = "isDefault", source = "default")
    public abstract WarehousePick toPick(Warehouse warehouse);

    public abstract ShiftOpening toOpening(OpenShiftRequest request);

    public abstract ShiftClosing toClosing(CloseShiftRequest request);
}
