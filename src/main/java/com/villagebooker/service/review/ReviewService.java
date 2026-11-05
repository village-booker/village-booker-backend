package com.villagebooker.service.review;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.PublicReviewResponse;
import com.villagebooker.dto.review.ReviewRequest;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Review;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.review.ReviewRepo;
import com.villagebooker.service.booking.BookingService;
import com.villagebooker.service.listing.OwnerListingService;
import com.villagebooker.service.listing.PublicListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

// The guest reviews a completed booking, once; everyone reads the reviews on the listing page.
@Service
@RequiredArgsConstructor
public class ReviewService {

    // counted from the day after the last booked date (check-out day for accommodation)
    public static final int REVIEW_DAYS = 14;

    private final ReviewRepo reviewRepo;
    private final BookingRepo bookingRepo;
    private final ListingRepo listingRepo;
    private final ListingRating listingRating;
    private final Clock clock;

    @Transactional
    public ReviewResponse create(Long guestId, Long bookingId, ReviewRequest request) {
        Long listingId = bookingRepo.findListingIdByIdAndGuestId(bookingId, guestId)
                .orElseThrow(() -> BookingService.bookingNotFound(bookingId));
        // the lock also makes a double submit wait and then find the first review
        Listing listing = listingRepo.findByIdForUpdate(listingId).orElseThrow();
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException(ErrorCode.REVIEW_NOT_ALLOWED, "Only a completed booking can be reviewed.",
                    Map.of("status", booking.getStatus().name()));
        }
        LocalDate lastDay = lastReviewDay(booking);
        if (AppTime.today(clock).isAfter(lastDay)) {
            throw new ConflictException(ErrorCode.REVIEW_PERIOD_OVER,
                    "A review can be written within " + REVIEW_DAYS + " days after the stay or tour.",
                    Map.of("days", REVIEW_DAYS, "lastDay", lastDay.toString()));
        }
        if (reviewRepo.existsByBookingId(bookingId)) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_EXISTS, "Booking " + bookingId + " already has a review.");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setRating(request.rating());
        review.setComment(blankToNull(request.comment()));
        reviewRepo.save(review);
        listingRating.recompute(listing);
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewResponse getMine(Long guestId, Long bookingId) {
        if (bookingRepo.findListingIdByIdAndGuestId(bookingId, guestId).isEmpty()) {
            throw BookingService.bookingNotFound(bookingId);
        }
        return reviewRepo.findByBookingId(bookingId)
                .map(ReviewResponse::from)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REVIEW_NOT_FOUND,
                        "Booking " + bookingId + " has no review."));
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> findForListing(Long listingId, Pageable pageable) {
        listingRepo.findById(listingId)
                .filter(l -> PublicListingService.VISIBLE.contains(l.getStatus()))
                .orElseThrow(() -> OwnerListingService.listingNotFound(listingId));
        return PageResponse.of(reviewRepo.findByBookingListingIdAndDeletedAtIsNull(listingId, pageable),
                PublicReviewResponse::from);
    }

    public static LocalDate lastReviewDay(Booking booking) {
        return booking.getEndDate().plusDays(1 + REVIEW_DAYS);
    }

    static NotFoundException reviewNotFound(Long reviewId) {
        return new NotFoundException(ErrorCode.REVIEW_NOT_FOUND, "Review " + reviewId + " not found.");
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
