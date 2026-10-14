package com.dl4j.salesforecast.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String,Object> badRequest(RuntimeException failure) { return error("invalid_request", failure.getMessage()); }

    @ExceptionHandler(EmptyResultDataAccessException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String,Object> notFound() { return error("not_found", "Resource was not found"); }

    @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String,Object> unavailable(Exception failure) { return error("service_unavailable", failure.getMessage()); }

    private Map<String,Object> error(String code, String message) {
        return Map.of("error", code, "message", message == null ? code : message, "timestamp", Instant.now().toString());
    }
}
