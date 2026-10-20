package com.villagebooker.common.exception;

// 404: the requested resource does not exist
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
