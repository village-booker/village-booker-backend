package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 403: authenticated, but not allowed to touch this resource (e.g. not the owner of the listing)
public class ForbiddenException extends ApiException {

    public ForbiddenException(ErrorCode code, String message) {
        super(code, message, null);
    }

    public ForbiddenException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
