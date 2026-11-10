package com.villagebooker.service.notification;

import com.villagebooker.enums.booking.BookingStatus;

public record BookingStatusChangedEvent(Long bookingId, BookingStatus from, BookingStatus to) {
}
