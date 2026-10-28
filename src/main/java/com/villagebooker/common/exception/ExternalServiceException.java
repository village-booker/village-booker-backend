package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 502: an external service (image storage, later payments) failed; details go to the log, not to the client
public class ExternalServiceException extends ApiException {

    public ExternalServiceException(ErrorCode code, String message) {
        super(code, message, null);
    }

    public ExternalServiceException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_GATEWAY;
    }
}
