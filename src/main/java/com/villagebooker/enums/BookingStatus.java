package com.villagebooker.enums;

import java.util.Set;

public enum BookingStatus {
    AWAITING_OWNER_APPROVAL,
    AWAITING_PAYMENT,
    CONFIRMED,
    REJECTED,
    EXPIRED,
    CANCELLED_BY_GUEST,
    CANCELLED_BY_OWNER,
    CANCELLED_MIN_NOT_REACHED,
    COMPLETED;

    // statuses that occupy dates (accommodation) or places (tour date)
    public static final Set<BookingStatus> ACTIVE = Set.of(AWAITING_OWNER_APPROVAL, AWAITING_PAYMENT, CONFIRMED);

    // statuses the scheduler moves to EXPIRED once expiresAt has passed
    public static final Set<BookingStatus> EXPIRABLE = Set.of(AWAITING_OWNER_APPROVAL, AWAITING_PAYMENT);

    // statuses that mean a booking is finished, positively or negatively
    public static final Set<BookingStatus> FINISHED = Set.of(REJECTED, EXPIRED, CANCELLED_BY_GUEST, CANCELLED_BY_OWNER, CANCELLED_MIN_NOT_REACHED, COMPLETED);
}
