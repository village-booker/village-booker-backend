package com.villagebooker.service.listing;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AccommodationRequest;
import com.villagebooker.dto.listing.ChangeRequestResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.dto.listing.TourRequest;
import com.villagebooker.enums.listing.ChangeRequestStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.ListingChangeRequest;
import com.villagebooker.model.Tour;
import com.villagebooker.repo.listing.ListingChangeRequestRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.user.UserRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

// Changes to a listing an admin has already decided on (APPROVED, REJECTED, PAUSED_BY_OWNER) go through a request:
// the owner sends the whole listing (same JSON as create), only the changed fields are stored, an admin approves
// (changes are applied, old values kept in previousValues) or rejects (listing stays as it was).
// Approving a request of a REJECTED listing also approves the listing: the fix and the re-review are one decision.
@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    // statuses whose listing may get a change request; PENDING is edited directly, DELETED is final
    private static final Set<ListingStatus> CHANGEABLE =
            Set.of(ListingStatus.APPROVED, ListingStatus.REJECTED, ListingStatus.PAUSED_BY_OWNER);

    private final ListingChangeRequestRepo changeRequestRepo;
    private final ListingRepo listingRepo;
    private final UserRepo userRepo;
    private final ListingStatusHistoryWriter historyWriter;
    private final Clock clock;

    @Transactional
    public ChangeRequestResponse requestAccommodationChange(Long ownerId, Long listingId, AccommodationRequest request) {
        Listing listing = lockOwnChangeable(ownerId, listingId);
        if (!(listing instanceof Accommodation)) {
            throw OwnerListingService.typeMismatch(listingId);
        }
        ListingRequests.check(request);
        Accommodation proposed = new Accommodation();
        ListingRequests.copy(request, proposed);
        return create(ownerId, listing, proposed);
    }

    @Transactional
    public ChangeRequestResponse requestTourChange(Long ownerId, Long listingId, TourRequest request) {
        Listing listing = lockOwnChangeable(ownerId, listingId);
        if (!(listing instanceof Tour)) {
            throw OwnerListingService.typeMismatch(listingId);
        }
        ListingRequests.check(request);
        Tour proposed = new Tour();
        ListingRequests.copy(request, proposed);
        return create(ownerId, listing, proposed);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeRequestResponse> findForOwnListing(Long ownerId, Long listingId, Pageable pageable) {
        Listing listing = listingRepo.findById(listingId)
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        OwnerListingService.checkOwner(listing, ownerId);
        return PageResponse.of(changeRequestRepo.findByListingId(listingId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeRequestResponse> findByStatus(ChangeRequestStatus status, Pageable pageable) {
        return PageResponse.of(changeRequestRepo.findByStatus(status, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public ChangeRequestResponse get(Long requestId) {
        return toResponse(find(requestId));
    }

    @Transactional
    public ChangeRequestResponse approve(Long adminId, Long requestId) {
        ListingChangeRequest request = find(requestId);
        Listing listing = lockListing(request.getListing().getId());
        requirePending(request);
        if (!CHANGEABLE.contains(listing.getStatus())) {
            throw notAllowed(listing);
        }

        Map<String, Object> changes = request.getRequestedChanges();
        request.setPreviousValues(ListingFields.valuesOf(listing, changes.keySet()));
        ListingFields.apply(listing, changes);

        if (listing.getStatus() == ListingStatus.REJECTED) {
            listing.setStatus(ListingStatus.APPROVED);
            listing.setRejectionReason(null);
            historyWriter.statusChanged(listing, ListingStatus.REJECTED, ListingStatus.APPROVED, adminId,
                    "Change request #" + request.getId() + " approved.");
        }
        close(request, ChangeRequestStatus.APPROVED, adminId);
        return toResponse(request);
    }

    @Transactional
    public ChangeRequestResponse reject(Long adminId, Long requestId, RejectionRequest rejection) {
        ListingChangeRequest request = find(requestId);
        requirePending(request);
        request.setRejectionReason(rejection.reason().trim());
        close(request, ChangeRequestStatus.REJECTED, adminId);
        return toResponse(request);
    }

    private ChangeRequestResponse create(Long ownerId, Listing listing, Listing proposed) {
        if (changeRequestRepo.existsByListingIdAndStatus(listing.getId(), ChangeRequestStatus.PENDING)) {
            throw new ConflictException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING,
                    "Listing " + listing.getId() + " already has a change request waiting for approval.");
        }
        Map<String, Object> changes = ListingFields.changes(listing, proposed);
        if (changes.isEmpty()) {
            throw new BadRequestException(ErrorCode.NO_CHANGES, "The request does not change anything.");
        }

        ListingChangeRequest request = new ListingChangeRequest();
        request.setListing(listing);
        request.setRequester(userRepo.getReferenceById(ownerId));
        request.setStatus(ChangeRequestStatus.PENDING);
        request.setRequestedChanges(changes);
        return toResponse(changeRequestRepo.save(request));
    }

    // lock: two requests for the same listing at once would both pass the "already pending" check
    private Listing lockOwnChangeable(Long ownerId, Long listingId) {
        Listing listing = lockListing(listingId);
        if (listing.getStatus() == ListingStatus.DELETED) {
            throw OwnerListingService.listingNotFound(listingId);
        }
        OwnerListingService.checkOwner(listing, ownerId);
        if (!CHANGEABLE.contains(listing.getStatus())) {
            throw notAllowed(listing);
        }
        return listing;
    }

    private Listing lockListing(Long listingId) {
        return listingRepo.findByIdForUpdate(listingId)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
    }

    private ListingChangeRequest find(Long requestId) {
        return changeRequestRepo.findById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHANGE_REQUEST_NOT_FOUND,
                        "Change request " + requestId + " not found."));
    }

    private static void requirePending(ListingChangeRequest request) {
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ConflictException(ErrorCode.CHANGE_REQUEST_NOT_PENDING,
                    "Change request " + request.getId() + " was already processed.",
                    Map.of("status", request.getStatus().name()));
        }
    }

    private static ConflictException notAllowed(Listing listing) {
        return new ConflictException(ErrorCode.CHANGE_REQUEST_NOT_ALLOWED,
                "A listing in status " + listing.getStatus() + " cannot get a change request.",
                Map.of("status", listing.getStatus().name()));
    }

    private void close(ListingChangeRequest request, ChangeRequestStatus status, Long adminId) {
        request.setStatus(status);
        request.setAdmin(userRepo.getReferenceById(adminId));
        request.setProcessedAt(Instant.now(clock));
    }

    private ChangeRequestResponse toResponse(ListingChangeRequest request) {
        Map<String, Object> oldValues = switch (request.getStatus()) {
            case PENDING -> ListingFields.valuesOf(request.getListing(), request.getRequestedChanges().keySet());
            case APPROVED -> request.getPreviousValues();
            case REJECTED -> null;
        };
        return ChangeRequestResponse.from(request, oldValues);
    }
}
