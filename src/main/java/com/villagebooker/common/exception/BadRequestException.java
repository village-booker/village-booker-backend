package com.villagebooker.common.exception;

// 400: a business rule was violated (e.g. guestCount > maxGuests); field format errors come from Bean Validation
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
