package com.aromatica.common.error;

import org.springframework.http.HttpStatus;

/**
 * Отказ по правилам предметной области: текст уходит клиенту как есть, статус задаёт подкласс.
 * Исключения JDK и Spring сюда не относятся — их служебный текст клиенту не показываем.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract HttpStatus status();
}
