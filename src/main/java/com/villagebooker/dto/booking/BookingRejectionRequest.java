package com.villagebooker.dto.booking;

import jakarta.validation.constraints.Size;

// the reason is optional when a host declines a booking request; the guest sees it
public record BookingRejectionRequest(@Size(max = 255) String reason) {
}
