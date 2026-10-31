package com.villagebooker.dto.booking;

import jakarta.validation.constraints.Size;

// guest cancels; the reason is optional
public record BookingCancellationRequest(@Size(max = 255) String reason) {
}
