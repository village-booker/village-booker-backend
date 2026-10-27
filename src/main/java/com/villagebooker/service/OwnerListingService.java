package com.villagebooker.service;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AccommodationRequest;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.dto.listing.TourRequest;
import com.villagebooker.enums.ListingStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.repo.ListingRepo;
import com.villagebooker.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// Host side of listings: create, read and edit own listings. Role OWNER is checked in the controller
// (@PreAuthorize), ownership of a single listing here.
// Cycle: create -> PENDING -> (admin) APPROVED / REJECTED. A PENDING listing (not reviewed yet) is edited directly;
// once an admin has decided, every change goes through a change request (ChangeRequestService).
@Service
@RequiredArgsConstructor
public class OwnerListingService {

    private final ListingRepo listingRepo;
    private final UserRepo userRepo;

    // a new listing waits for admin approval (PENDING); status, rating and reviewCount never come from the request
    @Transactional
    public ListingResponse createAccommodation(Long ownerId, AccommodationRequest request) {
        ListingRequests.check(request);
        Accommodation accommodation = new Accommodation();
        accommodation.setOwner(userRepo.getReferenceById(ownerId));
        accommodation.setStatus(ListingStatus.PENDING);
        ListingRequests.copy(request, accommodation);
        return ListingResponse.from(listingRepo.save(accommodation));
    }

    @Transactional
    public ListingResponse createTour(Long ownerId, TourRequest request) {
        ListingRequests.check(request);
        Tour tour = new Tour();
        tour.setOwner(userRepo.getReferenceById(ownerId));
        tour.setStatus(ListingStatus.PENDING);
        ListingRequests.copy(request, tour);
        return ListingResponse.from(listingRepo.save(tour));
    }

    // full replace of a PENDING listing
    @Transactional
    public ListingResponse updateAccommodation(Long ownerId, Long listingId, AccommodationRequest request) {
        if (!(findPendingOwn(ownerId, listingId) instanceof Accommodation accommodation)) {
            throw typeMismatch(listingId);
        }
        ListingRequests.check(request);
        ListingRequests.copy(request, accommodation);
        return ListingResponse.from(accommodation);
    }

    @Transactional
    public ListingResponse updateTour(Long ownerId, Long listingId, TourRequest request) {
        if (!(findPendingOwn(ownerId, listingId) instanceof Tour tour)) {
            throw typeMismatch(listingId);
        }
        ListingRequests.check(request);
        ListingRequests.copy(request, tour);
        return ListingResponse.from(tour);
    }

    // "moji oglasi": every status except DELETED, newest first
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> findMine(Long ownerId, Pageable pageable) {
        return PageResponse.of(
                listingRepo.findByOwnerIdAndStatusNot(ownerId, ListingStatus.DELETED, pageable),
                ListingSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ListingResponse getMine(Long ownerId, Long listingId) {
        return ListingResponse.from(findOwn(ownerId, listingId));
    }

    // own listing that is not DELETED: 404 if missing, 403 if it belongs to another host
    Listing findOwn(Long ownerId, Long listingId) {
        Listing listing = listingRepo.findById(listingId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .orElseThrow(() -> listingNotFound(listingId));
        checkOwner(listing, ownerId);
        // a lazy proxy is not an Accommodation / Tour, the real object behind it is
        return (Listing) Hibernate.unproxy(listing);
    }

    static NotFoundException listingNotFound(Long listingId) {
        return new NotFoundException(ErrorCode.LISTING_NOT_FOUND, "Listing " + listingId + " not found.");
    }

    static void checkOwner(Listing listing, Long ownerId) {
        if (!listing.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.NOT_LISTING_OWNER,
                    "Listing " + listing.getId() + " belongs to another host.");
        }
    }

    static BadRequestException typeMismatch(Long listingId) {
        return new BadRequestException(ErrorCode.LISTING_TYPE_MISMATCH,
                "Listing " + listingId + " is of a different type.");
    }

    private Listing findPendingOwn(Long ownerId, Long listingId) {
        Listing listing = findOwn(ownerId, listingId);
        if (listing.getStatus() != ListingStatus.PENDING) {
            throw new ConflictException(ErrorCode.LISTING_NOT_EDITABLE,
                    "Only a PENDING listing is edited directly; send a change request instead.",
                    Map.of("status", listing.getStatus().name()));
        }
        return listing;
    }
}
