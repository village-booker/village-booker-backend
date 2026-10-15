package com.villagebooker.repo;

import com.villagebooker.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepo extends JpaRepository<Review, Long> {

    Optional<Review> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    // reviews shown on a listing page
    Page<Review> findByBookingListingId(Long listingId, Pageable pageable);

    // recompute Listing.rating / reviewCount after a review is added (null if there are no reviews)
    @Query("select avg(r.rating) from Review r where r.booking.listing.id = :listingId")
    Double averageRatingForListing(@Param("listingId") Long listingId);

    long countByBookingListingId(Long listingId);
}
