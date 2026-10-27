package com.aromatica.common.web;

import lombok.Getter;

/** Слишком частые запросы: обработчик ошибок отвечает 429 с заголовком Retry-After. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(
            String message,
            long retryAfterSeconds
    ) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
