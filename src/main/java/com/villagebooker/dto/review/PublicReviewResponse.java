package com.villagebooker.dto.review;

import com.villagebooker.model.Review;
import com.villagebooker.model.User;

import java.time.Instant;
import java.time.LocalDate;

// A review on the listing page: the guest only by first name and picture, the stay only by its first day.
public record PublicReviewResponse(
        Long id,
        Integer rating,
        String comment,
        Instant createdAt,
        LocalDate stayStartDate,
        String guestFirstName,
        String guestImageUrl,
        String ownerReply,
        Instant ownerReplyAt
) {

    public static PublicReviewResponse from(Review review) {
        User guest = review.getBooking().getGuest();
        return new PublicReviewResponse(review.getId(), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getBooking().getStartDate(), guest.getFirstName(), guest.getImageUrl(),
                review.getOwnerReply(), review.getOwnerReplyAt());
    }
}
