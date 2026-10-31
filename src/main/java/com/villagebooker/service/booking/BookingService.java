package com.villagebooker.service.booking;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.booking.AccommodationBookingRequest;
import com.villagebooker.dto.booking.BookingResponse;
import com.villagebooker.dto.booking.TourBookingRequest;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.UnavailablePeriodRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.service.listing.ListingPrices;
import com.villagebooker.service.listing.OwnerListingService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingService {

    static final int MAX_DAYS_AHEAD = 365;

    private static final Set<TourDateStatus> BOOKABLE_DATE = Set.of(TourDateStatus.OPEN, TourDateStatus.CONFIRMED);

    private final BookingRepo bookingRepo;
    private final ListingRepo listingRepo;
    private final UnavailablePeriodRepo unavailablePeriodRepo;
    private final TourDateRepo tourDateRepo;
    private final UserRepo userRepo;
    private final BookingStatusChanger statusChanger;
    private final Clock clock;

    @Transactional
    public BookingResponse createAccommodation(Long guestId, AccommodationBookingRequest request) {
        LocalDate start = request.arrival();
        LocalDate end = request.departure().minusDays(1);
        LocalDate today = AppTime.today(clock);
        if (end.isBefore(start) || start.isBefore(today) || end.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BadRequestException(ErrorCode.BOOKING_DATES_INVALID,
                    "arrival must be today or later, departure after arrival and the last night at most "
                            + MAX_DAYS_AHEAD + " days ahead.",
                    Map.of("maxDays", MAX_DAYS_AHEAD));
        }

        // same lock as unavailable periods and listing deletion (spec 5.0): the checks below see a stable state
        Listing listing = lockBookableListing(request.listingId(), guestId);
        if (!(listing instanceof Accommodation accommodation)) {
            throw OwnerListingService.typeMismatch(listing.getId());
        }
        checkGuestCount(request.guestCount(), listing);
        if (bookingRepo.existsOverlapping(listing.getId(), start, end, BookingStatus.ACTIVE)
                || unavailablePeriodRepo.existsOverlapping(listing.getId(), start, end)) {
            throw new ConflictException(ErrorCode.DATES_NOT_AVAILABLE, "Some of these nights are not available.");
        }

        Booking booking = newBooking(guestId, listing, request.guestCount(), request.note(), start, end);
        BookingTerms.copy(listing, booking, ListingPrices.accommodationTotal(accommodation, request.guestCount(),
                request.arrival(), request.departure()));
        return BookingResponse.from(save(listing, booking));
    }

    @Transactional
    public BookingResponse createTour(Long guestId, TourBookingRequest request) {
        Long tourId = tourDateRepo.findTourIdById(request.tourDateId())
                .orElseThrow(() -> tourDateNotFound(request.tourDateId()));
        // lock order Listing -> TourDate (spec 5): listing deletion locks the listing too
        Listing listing = lockBookableListing(tourId, guestId);
        Tour tour = (Tour) listing;
        TourDate date = tourDateRepo.findByIdForUpdate(request.tourDateId())
                .orElseThrow(() -> tourDateNotFound(request.tourDateId()));
        if (!BOOKABLE_DATE.contains(date.getStatus()) || !date.getStartDate().isAfter(AppTime.today(clock))) {
            throw new ConflictException(ErrorCode.TOUR_DATE_NOT_BOOKABLE,
                    "Tour date " + date.getId() + " no longer takes bookings.",
                    Map.of("status", date.getStatus().name()));
        }
        int freePlaces = tour.getMaxGuests() - date.getBookedCount();
        if (request.guestCount() > freePlaces) {
            throw new ConflictException(ErrorCode.TOUR_DATE_CAPACITY_EXCEEDED,
                    "Only " + freePlaces + " places are left on this tour date.",
                    Map.of("freePlaces", freePlaces));
        }
        TourDateSeats.take(date, request.guestCount());

        Booking booking = newBooking(guestId, listing, request.guestCount(), request.note(),
                date.getStartDate(), date.getEndDate());
        booking.setTourDate(date);
        BookingTerms.copy(listing, booking, ListingPrices.tourTotal(tour, request.guestCount()));
        return BookingResponse.from(save(listing, booking));
    }

    // a listing that does not exist for guests (pending, rejected, deleted) is 404; a paused one is visible but closed
    private Listing lockBookableListing(Long listingId, Long guestId) {
        Listing listing = listingRepo.findByIdForUpdate(listingId)
                .filter(l -> l.getStatus() == ListingStatus.APPROVED || l.getStatus() == ListingStatus.PAUSED_BY_OWNER)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        if (listing.getStatus() != ListingStatus.APPROVED) {
            throw new ConflictException(ErrorCode.LISTING_NOT_BOOKABLE,
                    "Listing " + listingId + " does not take bookings at the moment.",
                    Map.of("status", listing.getStatus().name()));
        }
        if (listing.getOwner().getId().equals(guestId)) {
            throw new BadRequestException(ErrorCode.CANNOT_BOOK_OWN_LISTING, "A host cannot book their own listing.");
        }
        return listing;
    }

    // accommodation only: a tour's min / max count for the whole date (spec 1.3), checked as capacity
    private static void checkGuestCount(int guestCount, Listing listing) {
        if (guestCount < listing.getMinGuests() || guestCount > listing.getMaxGuests()) {
            throw new BadRequestException(ErrorCode.GUEST_COUNT_OUT_OF_RANGE,
                    "The number of guests must be between " + listing.getMinGuests() + " and "
                            + listing.getMaxGuests() + ".",
                    Map.of("min", listing.getMinGuests(), "max", listing.getMaxGuests()));
        }
    }

    private Booking newBooking(Long guestId, Listing listing, int guestCount, String note,
                               LocalDate start, LocalDate end) {
        Booking booking = new Booking();
        booking.setGuest(userRepo.getReferenceById(guestId));
        booking.setListing(listing);
        booking.setGuestCount(guestCount);
        booking.setNote(note == null || note.isBlank() ? null : note.trim());
        booking.setStartDate(start);
        booking.setEndDate(end);
        return booking;
    }

    private Booking save(Listing listing, Booking booking) {
        statusChanger.start(booking, BookingTerms.initialStatus(listing, booking));
        return bookingRepo.save(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findMine(Long guestId, Pageable pageable) {
        return PageResponse.of(bookingRepo.findByGuestId(guestId, pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse getMine(Long guestId, Long bookingId) {
        return BookingResponse.from(findOwn(guestId, bookingId));
    }

    // another guest's booking is 404, not 403: its existence is nobody else's business
    public Booking findOwn(Long guestId, Long bookingId) {
        return bookingRepo.findByIdAndGuestId(bookingId, guestId)
                .orElseThrow(() -> bookingNotFound(bookingId));
    }

    public static NotFoundException bookingNotFound(Long bookingId) {
        return new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + bookingId + " not found.");
    }

    private static NotFoundException tourDateNotFound(Long dateId) {
        return new NotFoundException(ErrorCode.TOUR_DATE_NOT_FOUND, "Tour date " + dateId + " not found.");
    }
}
