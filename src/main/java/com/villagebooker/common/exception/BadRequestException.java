package com.villagebooker.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 400: a business rule was violated (e.g. guestCount > maxGuests); field format errors come from Bean Validation
public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code, String message) {
        super(code, message, null);
    }

    public BadRequestException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
