package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// Base of the business exceptions. GlobalExceptionHandler turns it into ProblemDetail with
// status(), "code" and, if present, "params" (values the frontend puts into the translated text,
// e.g. {"maxGuests": 6} for "Najviše 6 gostiju").
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> params;

    protected ApiException(ErrorCode code, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    public abstract HttpStatus status();

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
