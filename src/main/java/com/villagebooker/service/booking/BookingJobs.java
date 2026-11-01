package com.villagebooker.service.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// The scheduler's steps (spec 8), one booking or tour date per transaction, so one failure does not undo the rest.
// Each step checks the state again: the list of ids may be stale by then (a guest, a host or another instance
// acted in between). Two instances doing the same step are stopped by the tour date lock and @Version on Booking.
@Component
@RequiredArgsConstructor
class BookingJobs {

    private static final Set<TourDateStatus> TAKING_PLACE = Set.of(TourDateStatus.CONFIRMED, TourDateStatus.FULL);

    private final BookingRepo bookingRepo;
    private final TourDateRepo tourDateRepo;
    private final BookingStatusChanger statusChanger;
    private final BookingCanceller canceller;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> bookingsToExpire() {
        return bookingRepo.findIdsToExpire(BookingStatus.EXPIRABLE, Instant.now(clock));
    }

    // the places are freed at the moment the booking expired, which can be the minimum deadline itself
    @Transactional
    public void expire(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        Instant expiresAt = booking.getExpiresAt();
        if (!BookingStatus.EXPIRABLE.contains(booking.getStatus()) || expiresAt.isAfter(Instant.now(clock))) {
            return;
        }
        statusChanger.moveTo(booking, BookingStatus.EXPIRED);
        if (booking.getTourDate() != null) {
            canceller.releasePlaces(booking, expiresAt);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> datesPastMinimumDeadline() {
        return tourDateRepo.findIdsPastDeadline(TourDateStatus.OPEN, Instant.now(clock));
    }

    // still OPEN after the deadline = below the minimum (TourDateSeats.take confirms a date at the minimum)
    @Transactional
    public void cancelBelowMinimum(Long dateId) {
        TourDate date = tourDateRepo.findByIdForUpdate(dateId).orElseThrow();
        if (date.getStatus() != TourDateStatus.OPEN || date.getMinGuestsDeadline().isAfter(Instant.now(clock))) {
            return;
        }
        for (Booking booking : bookingRepo.findByTourDateIdAndStatusIn(dateId, BookingStatus.ACTIVE)) {
            canceller.cancel(booking, BookingStatus.CANCELLED_MIN_NOT_REACHED, null);
        }
        date.setStatus(TourDateStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<Long> bookingsToComplete() {
        return bookingRepo.findIdsEndedBefore(BookingStatus.CONFIRMED, AppTime.today(clock));
    }

    @Transactional
    public void complete(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getEndDate().isBefore(AppTime.today(clock))) {
            statusChanger.moveTo(booking, BookingStatus.COMPLETED);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> datesToComplete() {
        return tourDateRepo.findIdsEndedBefore(TAKING_PLACE, AppTime.today(clock));
    }

    @Transactional
    public void completeDate(Long dateId) {
        TourDate date = tourDateRepo.findByIdForUpdate(dateId).orElseThrow();
        if (TAKING_PLACE.contains(date.getStatus()) && date.getEndDate().isBefore(AppTime.today(clock))) {
            date.setStatus(TourDateStatus.COMPLETED);
        }
    }
}
