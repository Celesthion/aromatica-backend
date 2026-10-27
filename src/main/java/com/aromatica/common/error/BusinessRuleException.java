package com.aromatica.common.error;

import org.springframework.http.HttpStatus;

/** Ввод нарушает правило предметной области (слабый пароль, дубль, неверная сумма): 400. */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
