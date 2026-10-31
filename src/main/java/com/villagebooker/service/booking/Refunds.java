package com.villagebooker.service.booking;

import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

// Refund table of spec 3. Reads only the terms copied onto the booking (spec 1.1), never the listing.
final class Refunds {

    static final BigDecimal FULL = BigDecimal.valueOf(100);

    private Refunds() {
    }

    // percentage of what was paid that goes back to the guest
    static BigDecimal percentage(Booking booking, BookingStatus cancelStatus, Instant now) {
        if (cancelStatus != BookingStatus.CANCELLED_BY_GUEST) {
            return FULL;
        }
        if (booking.getFreeCancellationUntil() != null && now.isBefore(booking.getFreeCancellationUntil())) {
            return FULL;
        }
        return switch (booking.getCancellationPolicy()) {
            case PARTIAL -> booking.getRefundPercentage();
            case FREE, NON_REFUNDABLE -> BigDecimal.ZERO;
        };
    }

    static BigDecimal amount(BigDecimal paid, BigDecimal percentage) {
        return paid.multiply(percentage).divide(FULL, 2, RoundingMode.HALF_UP);
    }
}
