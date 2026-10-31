package com.villagebooker.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the guest joins a tour date; dates and the tour come from it (spec 5.1)
public record TourBookingRequest(
        @NotNull Long tourDateId,
        @NotNull @Min(1) Integer guestCount,
        @Size(max = 255) String note
) {
}
