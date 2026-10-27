package com.aromatica.modules.pos.reservation.schedule;

import com.aromatica.modules.pos.reservation.service.ReservationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в час закрывает просроченные брони — статус нужен спискам и отчётам, резерв истёкшая
 * не держит и до этого. Повтор безопасен, несколько инстансов друг другу не мешают.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReservationExpiryJob {

    ReservationService reservationService;

    @Scheduled(cron = "${app.reservation.expiry-cron:0 5 * * * *}", zone = "Asia/Bishkek")
    public void expireOverdue() {
        int expired = reservationService.expireOverdue();
        if (expired > 0) {
            log.info("Просроченных броней закрыто: {}", expired);
        }
    }
}
