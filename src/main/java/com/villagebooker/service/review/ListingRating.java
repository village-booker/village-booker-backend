package com.villagebooker.service.review;

import com.villagebooker.model.Listing;
import com.villagebooker.repo.review.ReviewRepo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Listing.rating / reviewCount from all reviews an admin has not removed. Recomputed instead of adjusted, and always
// under the listing lock, so two reviews at once cannot both start from the old average.
@Component
@RequiredArgsConstructor
class ListingRating {

    private final ReviewRepo reviewRepo;
    private final EntityManager entityManager;

    // The listing must be locked (ListingRepo.findByIdForUpdate). Follow-on locking reads the row before it waits
    // for the lock, so a review that waited holds the version from before the other one's commit: without the refresh
    // its update would fail on @Version.
    void recompute(Listing listing) {
        entityManager.refresh(listing);
        Double average = reviewRepo.averageRatingForListing(listing.getId());
        listing.setRating(average == null ? null : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        listing.setReviewCount((int) reviewRepo.countByBookingListingIdAndDeletedAtIsNull(listing.getId()));
    }
}
