package com.villagebooker.service.tour;

import com.villagebooker.service.listing.OwnerListingService;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.booking.OwnerCancellationRequest;
import com.villagebooker.dto.tour.TourDateRequest;
import com.villagebooker.dto.tour.TourDateResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import com.villagebooker.service.booking.BookingCanceller;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Dates of a tour, managed by its owner (spec 5.1). Dates are not moved: a date without guests is deleted and
// created again; a date with guests is cancelled together with the bookings (spec 9.2).
@Service
@RequiredArgsConstructor
public class TourDateService {

    private final TourDateRepo tourDateRepo;
    private final ListingRepo listingRepo;
    private final BookingRepo bookingRepo;
    private final BookingCanceller bookingCanceller;
    private final Clock clock;

    @Transactional
    public TourDateResponse create(Long ownerId, Long tourId, TourDateRequest request) {
        // lock order Listing -> TourDate (spec 5); also serializes the "same start date" check
        Tour tour = lockOwnTour(ownerId, tourId);

        LocalDate start = request.startDate();
        if (!start.isAfter(AppTime.today(clock))) {
            throw new BadRequestException(ErrorCode.TOUR_DATE_IN_PAST, "A tour date must start tomorrow or later.");
        }
        Instant deadline = AppTime.startOfDay(start.minusDays(request.minGuestsDeadlineDaysBefore()));
        if (!deadline.isAfter(Instant.now(clock))) {
            throw new BadRequestException(ErrorCode.TOUR_DATE_DEADLINE_PASSED,
                    "The deadline for the minimum number of guests has already passed.");
        }
        if (tourDateRepo.existsByTourIdAndStartDateAndStatusNot(tourId, start, TourDateStatus.CANCELLED)) {
            throw new ConflictException(ErrorCode.TOUR_DATE_ALREADY_EXISTS,
                    "The tour already has a date starting on " + start + ".", Map.of("startDate", start.toString()));
        }

        TourDate date = new TourDate();
        date.setTour(tour);
        date.setStartDate(start);
        date.setEndDate(start.plusDays(tour.getDurationDays() - 1));
        date.setMinGuestsDeadline(deadline);
        return TourDateResponse.from(tourDateRepo.save(date));
    }

    @Transactional(readOnly = true)
    public List<TourDateResponse> findUpcoming(Long ownerId, Long tourId) {
        Listing listing = listingRepo.findById(tourId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .orElseThrow(() -> OwnerListingService.listingNotFound(tourId));
        OwnerListingService.checkOwner(listing, ownerId);
        return tourDateRepo.findByTourIdAndStartDateGreaterThanEqualOrderByStartDate(tourId, AppTime.today(clock))
                .stream().map(TourDateResponse::from).toList();
    }

    // spec 9.1: only without active bookings. Never booked -> row deleted; only old (cancelled/expired) bookings
    // -> status CANCELLED, so those bookings keep their date.
    @Transactional
    public void delete(Long ownerId, Long tourId, Long dateId) {
        lockOwnTour(ownerId, tourId);
        TourDate date = lockDate(tourId, dateId);
        if (date.getStatus() == TourDateStatus.CANCELLED) {
            return;
        }
        if (date.getStatus() == TourDateStatus.COMPLETED || date.getEndDate().isBefore(AppTime.today(clock))) {
            throw new BadRequestException(ErrorCode.TOUR_DATE_IN_PAST, "A past tour date cannot be removed.");
        }
        if (date.getBookedCount() > 0) {
            throw new ConflictException(ErrorCode.TOUR_DATE_HAS_BOOKINGS,
                    "The tour date has guests; it has to be cancelled together with their bookings.",
                    Map.of("bookedCount", date.getBookedCount()));
        }
        if (bookingRepo.existsByTourDateId(dateId)) {
            date.setStatus(TourDateStatus.CANCELLED);
        } else {
            tourDateRepo.delete(date);
        }
    }

    // spec 9.2: every active booking -> CANCELLED_BY_OWNER with a full refund, then the date itself. Cancelling
    // again does nothing; a date that has begun is not cancelled (same rule as for one booking).
    @Transactional
    public TourDateResponse cancel(Long ownerId, Long tourId, Long dateId, OwnerCancellationRequest request) {
        lockOwnTour(ownerId, tourId);
        TourDate date = lockDate(tourId, dateId);
        if (date.getStatus() == TourDateStatus.CANCELLED) {
            return TourDateResponse.from(date);
        }
        if (date.getStatus() == TourDateStatus.COMPLETED || !date.getStartDate().isAfter(AppTime.today(clock))) {
            throw new BadRequestException(ErrorCode.TOUR_DATE_IN_PAST, "A tour date that has begun cannot be cancelled.");
        }
        for (Booking booking : bookingRepo.findByTourDateIdAndStatusIn(dateId, BookingStatus.ACTIVE)) {
            bookingCanceller.cancel(booking, BookingStatus.CANCELLED_BY_OWNER, request.reason());
        }
        date.setStatus(TourDateStatus.CANCELLED);
        return TourDateResponse.from(date);
    }

    private TourDate lockDate(Long tourId, Long dateId) {
        return tourDateRepo.findByIdForUpdate(dateId)
                .filter(d -> d.getTour().getId().equals(tourId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOUR_DATE_NOT_FOUND,
                        "Tour date " + dateId + " not found on tour " + tourId + "."));
    }

    private Tour lockOwnTour(Long ownerId, Long tourId) {
        Listing listing = listingRepo.findByIdForUpdate(tourId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(tourId));
        OwnerListingService.checkOwner(listing, ownerId);
        if (!(listing instanceof Tour tour)) {
            throw OwnerListingService.typeMismatch(tourId);
        }
        return tour;
    }
}
