package com.aromatica.common.error;

import org.springframework.http.HttpStatus;

/** Действие не подходит к текущему состоянию записи (уже проведён, смена закрыта): 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
