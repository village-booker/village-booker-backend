package com.villagebooker.enums.listing;

import java.util.Set;

public enum ListingStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PAUSED_BY_OWNER,
    DELETED,
    // final; the host still sees it with removalReason, existing bookings go on as usual
    REMOVED_BY_ADMIN;

    // final statuses: out of the admin queues (change requests, images)
    public static final Set<ListingStatus> CLOSED = Set.of(DELETED, REMOVED_BY_ADMIN);
}
