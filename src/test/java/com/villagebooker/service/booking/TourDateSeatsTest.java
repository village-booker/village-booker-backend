package com.villagebooker.service.booking;

import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Tour;
import com.villagebooker.model.TourDate;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// spec 5.1 on a tour of 4..10 guests per date
class TourDateSeatsTest {

    private static final Instant DEADLINE = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant BEFORE_DEADLINE = DEADLINE.minusSeconds(1);
    private static final Instant AFTER_DEADLINE = DEADLINE.plusSeconds(1);

    @Test
    void dateIsConfirmedAtTheMinimumAndFullAtTheMaximum() {
        TourDate date = date(0, TourDateStatus.OPEN);

        TourDateSeats.take(date, 3);
        assertDate(date, 3, TourDateStatus.OPEN);
        TourDateSeats.take(date, 1);
        assertDate(date, 4, TourDateStatus.CONFIRMED);
        TourDateSeats.take(date, 5);
        assertDate(date, 9, TourDateStatus.CONFIRMED);
        TourDateSeats.take(date, 1);
        assertDate(date, 10, TourDateStatus.FULL);
    }

    @Test
    void releasingFromAFullDateConfirmsIt() {
        TourDate date = date(10, TourDateStatus.FULL);
        TourDateSeats.release(date, 2, BEFORE_DEADLINE);
        assertDate(date, 8, TourDateStatus.CONFIRMED);
    }

    @Test
    void belowTheMinimumBeforeTheDeadlineTheDateIsOpenAgain() {
        TourDate date = date(5, TourDateStatus.CONFIRMED);
        TourDateSeats.release(date, 2, BEFORE_DEADLINE);
        assertDate(date, 3, TourDateStatus.OPEN);

        TourDate full = date(10, TourDateStatus.FULL);
        TourDateSeats.release(full, 8, BEFORE_DEADLINE);
        assertDate(full, 2, TourDateStatus.OPEN);
    }

    @Test
    void afterTheDeadlineAConfirmedDateStaysConfirmed() {
        TourDate date = date(5, TourDateStatus.CONFIRMED);
        TourDateSeats.release(date, 2, AFTER_DEADLINE);
        assertDate(date, 3, TourDateStatus.CONFIRMED);

        TourDate full = date(10, TourDateStatus.FULL);
        TourDateSeats.release(full, 8, AFTER_DEADLINE);
        assertDate(full, 2, TourDateStatus.CONFIRMED);
    }

    @Test
    void releasingKeepsAnOpenOrCancelledStatus() {
        TourDate open = date(3, TourDateStatus.OPEN);
        TourDateSeats.release(open, 3, BEFORE_DEADLINE);
        assertDate(open, 0, TourDateStatus.OPEN);

        TourDate cancelled = date(6, TourDateStatus.CANCELLED);
        TourDateSeats.release(cancelled, 6, BEFORE_DEADLINE);
        assertDate(cancelled, 0, TourDateStatus.CANCELLED);
    }

    private static TourDate date(int booked, TourDateStatus status) {
        Tour tour = new Tour();
        tour.setMinGuests(4);
        tour.setMaxGuests(10);
        TourDate date = new TourDate();
        date.setTour(tour);
        date.setBookedCount(booked);
        date.setStatus(status);
        date.setMinGuestsDeadline(DEADLINE);
        return date;
    }

    private static void assertDate(TourDate date, int booked, TourDateStatus status) {
        assertThat(date.getBookedCount()).isEqualTo(booked);
        assertThat(date.getStatus()).isEqualTo(status);
    }
}
