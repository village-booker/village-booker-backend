package com.villagebooker.service.notification;

// the tour date reached its minimum of guests (OPEN -> CONFIRMED / FULL)
public record TourDateConfirmedEvent(Long tourDateId) {
}
