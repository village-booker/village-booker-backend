package com.villagebooker.service.listing;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.listing.ListingResponse;
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
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Pausing and deleting by the host (spec 9.3). Everything runs under the listing lock that booking creation takes,
// so no new booking slips in between the check and the status change.
@Service
@RequiredArgsConstructor
public class ListingLifecycleService {

    private static final Set<TourDateStatus> UPCOMING_DATE =
            Set.of(TourDateStatus.OPEN, TourDateStatus.CONFIRMED, TourDateStatus.FULL);

    private final ListingRepo listingRepo;
    private final BookingRepo bookingRepo;
    private final TourDateRepo tourDateRepo;
    private final ListingStatusHistoryWriter historyWriter;
    private final Clock clock;

    // no new bookings and out of the search; existing bookings go on as usual
    @Transactional
    public ListingResponse pause(Long ownerId, Long listingId) {
        Listing listing = lockOwn(ownerId, listingId);
        if (listing.getStatus() != ListingStatus.APPROVED) {
            throw new ConflictException(ErrorCode.LISTING_NOT_PAUSABLE, "Only an APPROVED listing can be paused.",
                    Map.of("status", listing.getStatus().name()));
        }
        changeStatus(listing, ListingStatus.PAUSED_BY_OWNER, ownerId);
        return ListingResponse.from(listing);
    }

    // back to APPROVED without the admin: the content was approved before the pause
    @Transactional
    public ListingResponse resume(Long ownerId, Long listingId) {
        Listing listing = lockOwn(ownerId, listingId);
        if (listing.getStatus() != ListingStatus.PAUSED_BY_OWNER) {
            throw new ConflictException(ErrorCode.LISTING_NOT_PAUSED, "Only a paused listing can be resumed.",
                    Map.of("status", listing.getStatus().name()));
        }
        changeStatus(listing, ListingStatus.APPROVED, ownerId);
        return ListingResponse.from(listing);
    }

    // Deleting never cancels bookings (team decision): with upcoming bookings the host gets 409 with their ids, pauses
    // the listing and deletes it once they are over or settled with the admin. A stay or tour that has already begun
    // does not block the deletion and ends as usual. Upcoming tour dates (then without active bookings) are cancelled.
    @Transactional
    public void delete(Long ownerId, Long listingId) {
        Listing listing = lockOwn(ownerId, listingId);
        LocalDate today = AppTime.today(clock);
        List<Booking> upcoming = bookingRepo.findByListingIdAndStatusInAndStartDateAfterOrderByStartDate(
                listingId, BookingStatus.ACTIVE, today);
        if (!upcoming.isEmpty()) {
            throw new ConflictException(ErrorCode.LISTING_HAS_ACTIVE_BOOKINGS,
                    "Listing " + listingId + " has " + upcoming.size()
                            + " upcoming bookings; pause it and delete it once they are over.",
                    Map.of("activeBookings", upcoming.size(), "bookingIds", upcoming.stream().map(Booking::getId).toList()));
        }
        if (listing instanceof Tour) {
            for (TourDate date : tourDateRepo.findByTourIdAndStartDateGreaterThanEqualOrderByStartDate(
                    listingId, today.plusDays(1))) {
                if (UPCOMING_DATE.contains(date.getStatus())) {
                    date.setStatus(TourDateStatus.CANCELLED);
                }
            }
        }
        changeStatus(listing, ListingStatus.DELETED, ownerId);
    }

    private Listing lockOwn(Long ownerId, Long listingId) {
        Listing listing = listingRepo.findByIdForUpdate(listingId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        OwnerListingService.checkOwner(listing, ownerId);
        return listing;
    }

    private void changeStatus(Listing listing, ListingStatus status, Long ownerId) {
        historyWriter.statusChanged(listing, listing.getStatus(), status, ownerId, null);
        listing.setStatus(status);
    }
}
