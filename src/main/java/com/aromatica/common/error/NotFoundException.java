package com.aromatica.common.error;

import org.springframework.http.HttpStatus;

/** Запрошенной записи нет: 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
