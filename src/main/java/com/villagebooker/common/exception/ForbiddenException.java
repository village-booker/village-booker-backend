package com.villagebooker.common.exception;

// 403: authenticated, but not allowed to touch this resource (e.g. not the owner of the listing)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
