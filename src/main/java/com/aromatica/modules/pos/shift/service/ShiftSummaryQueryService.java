package com.aromatica.modules.pos.shift.service;

import com.aromatica.modules.pos.shift.dtos.ShiftDtos.ShiftSummary;
import com.aromatica.modules.pos.shift.model.PosShift;

/**
 * Сводка смены (X-отчёт) по проведённым документам, с вкладом каждого продавца.
 */
public interface ShiftSummaryQueryService {

    ShiftSummary of(PosShift shift);

    /** Расчётный нал в кассе: размен + наличные продажи − наличные возвраты + наличные за сертификаты. */
    long expectedCashMinor(PosShift shift);
}
