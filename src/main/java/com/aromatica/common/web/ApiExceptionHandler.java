package com.aromatica.common.web;

import com.aromatica.common.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Ошибки API в формате, который ждёт фронт: {"error": "<текст>"}. */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    /** Отказы предметной области: текст для человека, статус задаёт тип исключения. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> domain(DomainException e) {
        return error(e.status(), e.getMessage());
    }

    /** Без этого обработчика фронт получает голое «Forbidden» без причины. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage() == null ? "Нет доступа" : e.getMessage());
    }

    /** Не глотаем 401: пусть решает entry point Spring Security. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> unauthorized(AuthenticationException e) {
        // свои отказы (неверный пароль, протухший refresh) бросаем BadCredentialsException с понятным текстом
        String message = e instanceof BadCredentialsException && e.getMessage() != null
                ? e.getMessage() : "Не авторизован";
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(e.getRetryAfterSeconds()))
                .body(Map.of("error", e.getMessage()));
    }

    /** Отказ базы объясняем по SQLState: общая фраза про уникальность сбивает с толку. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        String state = e.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : "";
        return switch (state == null ? "" : state) {
            // ссылка на запись, которой уже нет: товар или склад удалили
            case "23503" -> error(HttpStatus.CONFLICT,
                    "Запись ссылается на то, чего уже нет — обновите страницу");
            // не прошёл CHECK столбца, это ошибка ввода
            case "23514" -> error(HttpStatus.BAD_REQUEST,
                    "Значение не проходит проверку — исправьте ввод");
            // не влезает в столбец: огромное количество или сумма
            case "22003" -> error(HttpStatus.BAD_REQUEST, "Значение слишком большое");
            case "23502" -> error(HttpStatus.BAD_REQUEST, "Не заполнено обязательное поле");
            default -> error(HttpStatus.CONFLICT, "Конфликт данных — проверьте уникальность значений");
        };
    }

    /** Файл больше лимита multipart: отказ до контроллера, без обработчика это 500. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "Файл больше 3 МБ — сожмите фотографию");
    }

    /** Запись успели изменить в другом окне или запросе: правка поверх неё потеряла бы чужую. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> staleRecord(ObjectOptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "Запись только что изменили — обновите страницу и повторите");
    }

    /** Кривой UUID, битый JSON и т.п. — 400, а не 500; для чужого значения enum — поле и допустимые. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> badInput(Exception e) {
        Throwable cause = e instanceof HttpMessageNotReadableException r ? r.getMostSpecificCause() : null;
        if (cause instanceof InvalidFormatException f && f.getTargetType() != null && f.getTargetType().isEnum()) {
            String field = f.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .reduce((first, last) -> last)
                    .orElse("значение");
            String allowed = Arrays.stream(f.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return error(HttpStatus.BAD_REQUEST,
                    "Недопустимое значение поля " + field + ": " + f.getValue() + " (допустимо: " + allowed + ")");
        }
        return error(HttpStatus.BAD_REQUEST, "Некорректный запрос");
    }

    /** Нет обязательного параметра, файла или заголовка — 400 с его именем, а не 500. */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, String>> missingParam(Exception e) {
        String name = switch (e) {
            case MissingServletRequestParameterException x -> x.getParameterName();
            case MissingServletRequestPartException x -> x.getRequestPartName();
            case MissingRequestHeaderException x -> x.getHeaderName();
            default -> null;
        };
        return error(HttpStatus.BAD_REQUEST, name == null
                ? "Не передан обязательный параметр запроса"
                : "Не передан обязательный параметр: " + name);
    }

    /** Метод не поддержан на этом пути — 405, а не 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Метод не поддерживается");
    }

    /** Нет такого эндпоинта — 404, а не 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> noResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Ресурс не найден");
    }

    /** Ошибки @Valid: первое сообщение отдаём в обычном формате {"error": ...}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Некорректные данные");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /** Всё остальное: наружу без деталей, в лог целиком. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internal(Exception e) {
        log.error("Необработанная ошибка API", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка");
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? "Ошибка" : message));
    }
}
