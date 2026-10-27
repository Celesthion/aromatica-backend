package com.aromatica.modules.pos.shift.service;

import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import org.springframework.data.domain.Page;

/** Поиск смен для журнала: статус, период, номер. */
public interface ShiftSearchService {

    /** q — целое число ищет номер смены. */
    Page<PosShift> search(ShiftSearchRequest request);
}
