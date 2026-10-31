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
    DEPOSIT_NOT_ALLOWED_FOR_ON_SITE,
    LISTING_TYPE_MISMATCH,
    LISTING_NOT_EDITABLE,
    LISTING_NOT_PENDING,

    // listing change requests
    CHANGE_REQUEST_NOT_FOUND,
    CHANGE_REQUEST_NOT_ALLOWED,
    CHANGE_REQUEST_ALREADY_PENDING,
    CHANGE_REQUEST_NOT_PENDING,
    NO_CHANGES,

    // features (admin catalog)
    FEATURE_NOT_FOUND,
    FEATURE_NAME_TAKEN,
    FEATURE_IN_USE,

    // listing images
    IMAGE_NOT_FOUND,
    IMAGE_INVALID_TYPE,
    IMAGE_TOO_LARGE,
    IMAGE_LIMIT_REACHED,
    IMAGE_ORDER_INVALID,
    IMAGE_UPLOAD_FAILED,
    IMAGE_NOT_PENDING,

    // tour dates
    TOUR_DATE_NOT_FOUND,
    TOUR_DATE_IN_PAST,
    TOUR_DATE_ALREADY_EXISTS,
    TOUR_DATE_DEADLINE_PASSED,
    TOUR_DATE_HAS_BOOKINGS,

    // unavailable periods (owner blocks nights)
    UNAVAILABLE_PERIOD_NOT_FOUND,
    PERIOD_OVERLAPS_BOOKING,

    // public search
    DATE_RANGE_INVALID,
    PRICE_FILTER_NEEDS_TYPE,
    PRICE_RANGE_INVALID,

    // bookings
    BOOKING_NOT_FOUND,
    LISTING_NOT_BOOKABLE,
    CANNOT_BOOK_OWN_LISTING,
    BOOKING_DATES_INVALID,
    GUEST_COUNT_OUT_OF_RANGE,
    DATES_NOT_AVAILABLE,
    TOUR_DATE_NOT_BOOKABLE,
    TOUR_DATE_CAPACITY_EXCEEDED,
    BOOKING_STATUS_INVALID,
    BOOKING_EXPIRED,

    // pausing and deleting a listing (spec 9.3)
    LISTING_NOT_PAUSABLE,
    LISTING_HAS_ACTIVE_BOOKINGS
}
