package com.villagebooker.service.booking;

import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Tour;
import com.villagebooker.model.TourDate;

import java.time.Instant;

// bookedCount and the status it decides (spec 5.1); only under the tour date lock (TourDateRepo.findByIdForUpdate)
final class TourDateSeats {

    private TourDateSeats() {
    }

    static void take(TourDate date, int guestCount) {
        Tour tour = date.getTour();
        int booked = date.getBookedCount() + guestCount;
        date.setBookedCount(booked);
        if (booked == tour.getMaxGuests()) {
            date.setStatus(TourDateStatus.FULL);
        } else if (booked >= tour.getMinGuests()) {
            date.setStatus(TourDateStatus.CONFIRMED);
        }
    }

    // after the minimum deadline a confirmed date stays confirmed even below the minimum: the tour takes place
    static void release(TourDate date, int guestCount, Instant now) {
        int booked = date.getBookedCount() - guestCount;
        date.setBookedCount(booked);
        if (date.getStatus() != TourDateStatus.FULL && date.getStatus() != TourDateStatus.CONFIRMED) {
            return;
        }
        if (booked < date.getTour().getMinGuests() && now.isBefore(date.getMinGuestsDeadline())) {
            date.setStatus(TourDateStatus.OPEN);
        } else {
            date.setStatus(TourDateStatus.CONFIRMED);
        }
    }
}
