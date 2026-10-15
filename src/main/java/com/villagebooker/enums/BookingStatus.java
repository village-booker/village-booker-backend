package com.villagebooker.enums;

public enum BookingStatus {
    AWAITING_OWNER_APPROVAL,
    AWAITING_PAYMENT,
    CONFIRMED,
    REJECTED,
    EXPIRED,
    CANCELLED_BY_GUEST,
    CANCELLED_BY_OWNER,
    CANCELLED_MIN_NOT_REACHED,
    COMPLETED
}
