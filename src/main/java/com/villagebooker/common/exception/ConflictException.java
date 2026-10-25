package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 409: the request conflicts with the current state (dates taken, email already registered...)
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, String message) {
        super(code, message, null);
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
