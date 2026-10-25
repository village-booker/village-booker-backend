package com.villagebooker.common.exception;

// Stable error codes sent to the client as ProblemDetail "code". The frontend translates them (sr / en),
// so a code is never renamed once the frontend uses it; "detail" stays English and is meant for developers.
// New business rule -> new code here, next to the others of its area.
public enum ErrorCode {

    // generic request errors (Spring MVC, validation)
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    ROUTE_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    REQUEST_ERROR,

    // security
    AUTHENTICATION_REQUIRED,
    INVALID_CREDENTIALS,
    ACCESS_DENIED,

    // data and server
    CONCURRENT_MODIFICATION,
    DATA_CONFLICT,
    INTERNAL_ERROR,

    // auth / users
    EMAIL_ALREADY_REGISTERED,
    PASSWORD_TOO_LONG,
    USER_NOT_FOUND,
    USER_ALREADY_HOST,
    ADMIN_CANNOT_BE_HOST,
    INVALID_BANK_ACCOUNT,

    // listings
    LISTING_NOT_FOUND,
    NOT_LISTING_OWNER,
    GUEST_RANGE_INVALID,
    BASE_GUESTS_OUT_OF_RANGE,
    FREE_CANCELLATION_DAYS_REQUIRED,
    FREE_CANCELLATION_DAYS_NOT_ALLOWED,
    REFUND_PERCENTAGE_REQUIRED,
    REFUND_PERCENTAGE_NOT_ALLOWED,
    DEPOSIT_NOT_ALLOWED_FOR_ON_SITE
}
