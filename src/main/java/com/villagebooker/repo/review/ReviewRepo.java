package com.villagebooker.repo.review;

import com.villagebooker.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepo extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Optional<Review> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    // only the id, so the listing lock is the first load of the listing in the transaction
    @Query("select r.booking.listing.id from Review r where r.id = :id")
    Optional<Long> findListingIdById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Optional<Review> findWithBookingById(Long id);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    List<Review> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Optional<Review> findByIdAndBookingListingOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"booking", "booking.guest"})
    Page<Review> findByBookingListingIdAndDeletedAtIsNull(Long listingId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Page<Review> findByBookingListingOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Page<Review> findByBookingListingOwnerIdAndBookingListingIdAndDeletedAtIsNull(Long ownerId, Long listingId,
                                                                                  Pageable pageable);

    // admin: deleted ones too
    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Page<Review> findByBookingListingId(Long listingId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.listing", "booking.guest"})
    Page<Review> findAllBy(Pageable pageable);

    @Modifying
    @Query("""
            update Review r set r.ownerReply = :reply, r.ownerReplyAt = :at
            where r.id = :id and r.ownerReply is null""")
    int replyIfAbsent(@Param("id") Long id, @Param("reply") String reply, @Param("at") Instant at);

    // recompute Listing.rating / reviewCount (null if there are no reviews)
    @Query("select avg(r.rating) from Review r where r.booking.listing.id = :listingId and r.deletedAt is null")
    Double averageRatingForListing(@Param("listingId") Long listingId);

    long countByBookingListingIdAndDeletedAtIsNull(Long listingId);
}
