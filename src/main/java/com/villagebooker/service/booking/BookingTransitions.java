package com.villagebooker.service.booking;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.enums.booking.BookingStatus;

import java.util.Map;
import java.util.Set;

import static com.villagebooker.enums.booking.BookingStatus.*;

// Allowed status changes (spec 3). Finished statuses have no way out.
final class BookingTransitions {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            AWAITING_OWNER_APPROVAL, Set.of(AWAITING_PAYMENT, CONFIRMED, REJECTED, EXPIRED,
                    CANCELLED_BY_GUEST, CANCELLED_BY_OWNER, CANCELLED_MIN_NOT_REACHED),
            AWAITING_PAYMENT, Set.of(CONFIRMED, EXPIRED,
                    CANCELLED_BY_GUEST, CANCELLED_BY_OWNER, CANCELLED_MIN_NOT_REACHED),
            CONFIRMED, Set.of(COMPLETED,
                    CANCELLED_BY_GUEST, CANCELLED_BY_OWNER, CANCELLED_MIN_NOT_REACHED));

    private BookingTransitions() {
    }

    static boolean isAllowed(BookingStatus from, BookingStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    static void check(BookingStatus from, BookingStatus to) {
        if (!isAllowed(from, to)) {
            throw new ConflictException(ErrorCode.BOOKING_STATUS_INVALID,
                    "A booking in status " + from + " cannot become " + to + ".",
                    Map.of("status", from.name(), "targetStatus", to.name()));
        }
    }}
