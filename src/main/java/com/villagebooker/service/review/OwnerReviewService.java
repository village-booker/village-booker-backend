package com.villagebooker.service.review;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.ReviewReplyRequest;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.model.Review;
import com.villagebooker.repo.review.ReviewRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// Reviews of the host's own listings and the host's public reply (one per review, not edited afterwards).
@Service
@RequiredArgsConstructor
public class OwnerReviewService {

    private final ReviewRepo reviewRepo;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findMine(Long ownerId, Long listingId, Pageable pageable) {
        Page<Review> page = listingId == null
                ? reviewRepo.findByBookingListingOwnerIdAndDeletedAtIsNull(ownerId, pageable)
                : reviewRepo.findByBookingListingOwnerIdAndBookingListingIdAndDeletedAtIsNull(ownerId, listingId, pageable);
        return PageResponse.of(page, ReviewResponse::from);
    }

    // a review of another host's listing is 404, like a booking
    @Transactional
    public ReviewResponse reply(Long ownerId, Long reviewId, ReviewReplyRequest request) {
        Review review = reviewRepo.findByIdAndBookingListingOwnerIdAndDeletedAtIsNull(reviewId, ownerId)
                .orElseThrow(() -> ReviewService.reviewNotFound(reviewId));
        String reply = request.reply().strip();
        Instant now = Instant.now(clock);
        // conditional update instead of a check in Java, so two replies sent at once cannot both pass
        if (reviewRepo.replyIfAbsent(reviewId, reply, now) == 0) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_REPLIED, "Review " + reviewId + " already has a reply.");
        }
        review.setOwnerReply(reply);
        review.setOwnerReplyAt(now);
        return ReviewResponse.from(review);
    }
}
