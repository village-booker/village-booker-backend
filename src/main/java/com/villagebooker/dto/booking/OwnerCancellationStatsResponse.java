package com.villagebooker.dto.booking;

import java.time.Instant;

// one host in the admin's list of cancellations by hosts (spec 9.4); built in BookingRepo
public record OwnerCancellationStatsResponse(
        Long ownerId,
        String firstName,
        String lastName,
        String email,
        long cancellations,
        Instant lastCancelledAt
) {
}
