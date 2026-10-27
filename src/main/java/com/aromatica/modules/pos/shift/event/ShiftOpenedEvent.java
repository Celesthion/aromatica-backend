package com.aromatica.modules.pos.shift.event;

import com.aromatica.modules.pos.shift.model.PosShift;

public record ShiftOpenedEvent(
        PosShift shift
) {
}
