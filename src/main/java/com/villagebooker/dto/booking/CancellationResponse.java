package com.villagebooker.dto.booking;

import java.math.BigDecimal;

// paidAmount = successful payments; refundAmount = what goes back (spec 3). Before cancelling it is a preview.
public record CancellationResponse(
        BookingResponse booking,
        BigDecimal paidAmount,
        BigDecimal refundAmount
) {
}
