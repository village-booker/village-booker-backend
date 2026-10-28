package com.villagebooker.dto.listing;

import java.time.LocalDate;
import java.util.List;

// Taken nights of accommodation between from and to. Bookings and blocked periods are merged on purpose,
// so a guest cannot tell one from the other.
public record AvailabilityResponse(LocalDate from, LocalDate to, List<DateRangeResponse> takenNights) {
}
