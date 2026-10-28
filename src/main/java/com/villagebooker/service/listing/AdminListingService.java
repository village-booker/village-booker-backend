package com.villagebooker.service.listing;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AdminListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.model.Listing;
import com.villagebooker.repo.listing.ListingRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// Two admins deciding on the same listing at once: @Version on Listing makes the second one fail with 409.
@Service
@RequiredArgsConstructor
public class AdminListingService {

    private final ListingRepo listingRepo;
    private final ListingStatusHistoryWriter historyWriter;
    private final MainImages mainImages;

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> findByStatus(ListingStatus status, Pageable pageable) {
        Page<Listing> page = listingRepo.findByStatus(status, pageable);
        Map<Long, String> images = mainImages.forOwnerOrAdmin(page.getContent());
        return PageResponse.of(page, l -> ListingSummaryResponse.from(l, images.get(l.getId())));
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
