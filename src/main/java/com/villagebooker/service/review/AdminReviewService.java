package com.villagebooker.service.review;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Review;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.review.ReviewRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// The admin removes an offensive or false review, or only the host's reply. A removed review keeps its row
// (reviews are never deleted, spec 9.1), is hidden everywhere and no longer counts in the listing's rating.
@Service
@RequiredArgsConstructor
public class AdminReviewService {

    private final ReviewRepo reviewRepo;
    private final ListingRepo listingRepo;
    private final ListingRating listingRating;
    private final Clock clock;

    // removed ones too, so the admin sees what was removed
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> find(Long listingId, Pageable pageable) {
        Page<Review> page = listingId == null
                ? reviewRepo.findAllBy(pageable)
                : reviewRepo.findByBookingListingId(listingId, pageable);
        return PageResponse.of(page, ReviewResponse::from);
    }

    @Transactional
    public void delete(Long reviewId) {
        Long listingId = reviewRepo.findListingIdById(reviewId)
                .orElseThrow(() -> ReviewService.reviewNotFound(reviewId));
        Listing listing = listingRepo.findByIdForUpdate(listingId).orElseThrow();
        Review review = reviewRepo.findWithBookingById(reviewId).orElseThrow();
        if (review.getDeletedAt() == null) {
            review.setDeletedAt(Instant.now(clock));
            listingRating.recompute(listing);
        }
    }

    // the host may reply again afterward
    @Transactional
    public void deleteReply(Long reviewId) {
        Review review = reviewRepo.findWithBookingById(reviewId)
                .orElseThrow(() -> ReviewService.reviewNotFound(reviewId));
        review.setOwnerReply(null);
        review.setOwnerReplyAt(null);
    }
}
