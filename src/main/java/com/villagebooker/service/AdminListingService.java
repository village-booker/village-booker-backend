package com.villagebooker.service;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AdminListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.ListingStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.repo.ListingRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// Admin moderation of new and resubmitted listings: PENDING -> APPROVED / REJECTED.
// Two admins deciding on the same listing at once: @Version on Listing makes the second one fail with 409.
@Service
@RequiredArgsConstructor
public class AdminListingService {

    private final ListingRepo listingRepo;
    private final ListingStatusHistoryWriter historyWriter;

    // the admin queue, oldest first (sort comes from the controller)
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> findByStatus(ListingStatus status, Pageable pageable) {
        return PageResponse.of(listingRepo.findByStatus(status, pageable), ListingSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminListingResponse get(Long listingId) {
        return AdminListingResponse.from(find(listingId));
    }

    @Transactional
    public AdminListingResponse approve(Long adminId, Long listingId) {
        Listing listing = findPending(listingId);
        listing.setStatus(ListingStatus.APPROVED);
        historyWriter.statusChanged(listing, ListingStatus.PENDING, ListingStatus.APPROVED, adminId, null);
        return AdminListingResponse.from(listing);
    }

    @Transactional
    public AdminListingResponse reject(Long adminId, Long listingId, RejectionRequest request) {
        Listing listing = findPending(listingId);
        String reason = request.reason().trim();
        listing.setStatus(ListingStatus.REJECTED);
        listing.setRejectionReason(reason);
        historyWriter.statusChanged(listing, ListingStatus.PENDING, ListingStatus.REJECTED, adminId, reason);
        return AdminListingResponse.from(listing);
    }

    private Listing find(Long listingId) {
        return listingRepo.findById(listingId)
                .map(l -> (Listing) Hibernate.unproxy(l))
                .orElseThrow(() -> new NotFoundException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing " + listingId + " not found."));
    }

    // only a listing waiting in the queue can be decided on; anything else was already handled
    private Listing findPending(Long listingId) {
        Listing listing = find(listingId);
        if (listing.getStatus() != ListingStatus.PENDING) {
            throw new ConflictException(ErrorCode.LISTING_NOT_PENDING,
                    "Listing " + listingId + " is not waiting for approval.",
                    Map.of("status", listing.getStatus().name()));
        }
        return listing;
    }
}
