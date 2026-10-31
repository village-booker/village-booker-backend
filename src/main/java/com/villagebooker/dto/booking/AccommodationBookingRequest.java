package com.villagebooker.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// arrival / departure as the guest picks them (same as the search); the booking stores nights [arrival, departure - 1]
public record AccommodationBookingRequest(
        @NotNull Long listingId,
        @NotNull LocalDate arrival,
        @NotNull LocalDate departure,
        @NotNull @Min(1) Integer guestCount,
        @Size(max = 255) String note
) {
}
