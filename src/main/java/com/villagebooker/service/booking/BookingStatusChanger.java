package com.villagebooker.service.booking;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.config.BookingProperties;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.TourDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Every status change of a booking goes through here (spec 3, 8): the transition is checked and expiresAt is set
// on entering a status that can expire and cleared on leaving it.
@Component
@RequiredArgsConstructor
public class BookingStatusChanger {

    private final BookingProperties properties;
    private final Clock clock;

    public void moveTo(Booking booking, BookingStatus status) {
        BookingTransitions.check(booking.getStatus(), status);
        set(booking, status);
    }

    // the scheduler expires bookings later (spec 8), so until then an expired one still has its old status
    public void requireNotExpired(Booking booking) {
        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ConflictException(ErrorCode.BOOKING_EXPIRED, "Booking " + booking.getId() + " has expired.");
        }
    }

    void start(Booking booking, BookingStatus status) {
        set(booking, status);
    }

    private void set(Booking booking, BookingStatus status) {
        booking.setStatus(status);
        Instant now = Instant.now(clock);
        booking.setExpiresAt(switch (status) {
            case AWAITING_OWNER_APPROVAL -> expiresAt(booking, now, properties.ownerApprovalTimeout());
            case AWAITING_PAYMENT -> expiresAt(booking, now, properties.paymentTimeout());
            default -> null;
        });
    }

    // spec 8: a request is settled by the start of the stay / tour at the latest; for a tour before its
    // minimum-guests deadline, by that deadline, so the count at the deadline holds only settled bookings
    static Instant expiresAt(Booking booking, Instant now, Duration timeout) {
        Instant latest = AppTime.startOfDay(booking.getStartDate());
        TourDate date = booking.getTourDate();
        if (date != null && now.isBefore(date.getMinGuestsDeadline())) {
            latest = date.getMinGuestsDeadline();
        }
        Instant expiresAt = now.plus(timeout);
        return expiresAt.isBefore(latest) ? expiresAt : latest;
    }
}
