package com.villagebooker.service.listing;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.listing.UnavailablePeriodRequest;
import com.villagebooker.dto.listing.UnavailablePeriodResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.UnavailablePeriod;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.listing.UnavailablePeriodRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Nights an owner blocks on accommodation. Overlapping another block is fine (the guest calendar merges them),
// overlapping an active booking is not: a guest who already booked must not lose the stay.
@Service
@RequiredArgsConstructor
public class UnavailablePeriodService {

    static final int MAX_DAYS_AHEAD = 365;

    private final UnavailablePeriodRepo unavailablePeriodRepo;
    private final ListingRepo listingRepo;
    private final BookingRepo bookingRepo;
    private final OwnerListingService ownerListingService;
    private final Clock clock;

    @Transactional
    public UnavailablePeriodResponse create(Long ownerId, Long listingId, UnavailablePeriodRequest request) {
        // same lock as booking creation (spec 5.0): a booking and a block of the same night cannot both pass
        Listing listing = listingRepo.findByIdForUpdate(listingId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        OwnerListingService.checkOwner(listing, ownerId);
        requireAccommodation(listing);

        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        LocalDate today = AppTime.today(clock);
        if (end.isBefore(start) || start.isBefore(today) || end.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BadRequestException(ErrorCode.DATE_RANGE_INVALID,
                    "startDate must be today or later, endDate not before startDate and at most "
                            + MAX_DAYS_AHEAD + " days ahead.",
                    Map.of("maxDays", MAX_DAYS_AHEAD));
        }
        if (bookingRepo.existsOverlapping(listingId, start, end, BookingStatus.ACTIVE)) {
            throw new ConflictException(ErrorCode.PERIOD_OVERLAPS_BOOKING,
                    "Some of these nights are already booked.");
        }

        UnavailablePeriod period = new UnavailablePeriod();
        period.setListing(listing);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        return UnavailablePeriodResponse.from(unavailablePeriodRepo.save(period));
    }

    // periods that are not over yet
    @Transactional(readOnly = true)
    public List<UnavailablePeriodResponse> findUpcoming(Long ownerId, Long listingId) {
        requireAccommodation(ownerListingService.findOwn(ownerId, listingId));
        return unavailablePeriodRepo
                .findByListingIdAndEndDateGreaterThanEqualOrderByStartDate(listingId, AppTime.today(clock)).stream()
                .map(UnavailablePeriodResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long ownerId, Long listingId, Long periodId) {
        ownerListingService.findOwn(ownerId, listingId);
        UnavailablePeriod period = unavailablePeriodRepo.findById(periodId)
                .filter(p -> p.getListing().getId().equals(listingId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.UNAVAILABLE_PERIOD_NOT_FOUND,
                        "Unavailable period " + periodId + " not found on listing " + listingId + "."));
        unavailablePeriodRepo.delete(period);
    }

    private static void requireAccommodation(Listing listing) {
        if (!(listing instanceof Accommodation)) {
            throw OwnerListingService.typeMismatch(listing.getId());
        }
    }
}
