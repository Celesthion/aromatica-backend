package com.aromatica.security.auth.schedule;

import com.aromatica.security.auth.service.RefreshTokenService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Без ночной чистки таблица растёт: каждая ротация оставляет строку. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RefreshTokenCleanupJob {

    RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${app.auth.token-cleanup-cron:0 30 3 * * *}", zone = "Asia/Bishkek")
    public void deleteSpent() {
        int deleted = refreshTokenService.deleteSpent();
        if (deleted > 0) {
            log.info("Отработавших refresh-токенов удалено: {}", deleted);
        }
    }
}
