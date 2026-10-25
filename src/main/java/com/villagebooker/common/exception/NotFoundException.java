package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 404: the requested resource does not exist
public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode code, String message) {
        super(code, message, null);
    }

    public NotFoundException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
