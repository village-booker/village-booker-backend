package com.villagebooker.common.exception;

// 409: the request conflicts with the current state (dates taken, email already registered...)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
