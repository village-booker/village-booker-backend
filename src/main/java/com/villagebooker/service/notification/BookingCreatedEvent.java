package com.villagebooker.service.notification;

// published in the booking's transaction; only ids go into events (spec 14)
public record BookingCreatedEvent(Long bookingId) {
}
