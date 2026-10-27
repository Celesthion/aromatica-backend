package com.aromatica.modules.pos.shift.service;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.model.ShiftClosing;
import com.aromatica.modules.pos.shift.model.ShiftOpening;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosShiftService {

    PosShift openShift(ShiftOpening opening, String login);

    PosShift closeShift(UUID shiftId, ShiftClosing closing, String login);

    Optional<PosShift> currentShift(String login);

    /** Если открытых смен несколько, терминал предлагает выбрать. Не-админ видит только свои склады. */
    List<PosShift> openShifts(String login);

    /** Проведённые чеки и возвраты смены, свежие сверху. */
    List<StockDoc> shiftReceipts(UUID shiftId, String login);

    /** Проверяет доступ к складу смены; общая точка для сервисов кассы. */
    PosShift requireAccessibleShift(UUID shiftId, String login);

    /** Открытая смена для чтения (черновики, списки); без блокировки. */
    PosShift requireAccessibleOpenShift(UUID shiftId, String login);

    /**
     * Открытая смена для операции, которая пишет в неё (чек, возврат, сторно, списание):
     * под разделяемой блокировкой, чтобы закрытие не посчитало итог посреди операции.
     */
    PosShift lockOpenShift(UUID shiftId, String login);
}
