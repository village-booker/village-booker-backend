package com.villagebooker.dto.review;

import com.villagebooker.model.Booking;
import com.villagebooker.model.Review;
import com.villagebooker.model.User;

import java.time.Instant;
import java.time.LocalDate;

// A review as its guest, the host and the admin see it. deletedAt is set when an admin removed it.
public record ReviewResponse(
        Long id,
        Long bookingId,
        Long listingId,
        String listingName,
        LocalDate startDate,
        LocalDate endDate,
        Integer rating,
        String comment,
        Instant createdAt,
        Long guestId,
        String guestFirstName,
        String guestImageUrl,
        String ownerReply,
        Instant ownerReplyAt,
        Instant deletedAt
) {

    public static ReviewResponse from(Review review) {
        Booking booking = review.getBooking();
        User guest = booking.getGuest();
        return new ReviewResponse(review.getId(), booking.getId(), booking.getListing().getId(),
                booking.getListing().getName(), booking.getStartDate(), booking.getEndDate(), review.getRating(),
                review.getComment(), review.getCreatedAt(), guest.getId(), guest.getFirstName(),
                guest.getImageUrl(), review.getOwnerReply(), review.getOwnerReplyAt(), review.getDeletedAt());
    }
}
