package com.villagebooker.repo.booking;

import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// JpaSpecificationExecutor: the owner's list with optional filters (status, listing)
public interface BookingRepo extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    // listing (and guest) loaded in the same query, otherwise mapping to DTO makes N+1 queries
    @EntityGraph(attributePaths = "listing")
    Page<Booking> findByGuestId(Long guestId, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "guest"})
    Page<Booking> findByListingOwnerId(Long ownerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"listing", "guest"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @EntityGraph(attributePaths = "listing")
    Optional<Booking> findByIdAndGuestId(Long id, Long guestId);

    @EntityGraph(attributePaths = {"listing", "guest"})
    Optional<Booking> findByIdAndListingOwnerId(Long id, Long ownerId);

    // accommodation availability: closed intervals [start, end] overlap (spec 1.2); tour bookings are excluded
    @Query("""
            select case when count(b) > 0 then true else false end
            from Booking b
            where b.listing.id = :listingId
              and b.tourDate is null
              and b.status in :statuses
              and b.startDate <= :endDate
              and :startDate <= b.endDate
            """)
    boolean existsOverlapping(@Param("listingId") Long listingId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("statuses") Collection<BookingStatus> statuses);

    // taken nights of an accommodation in [from, to] for the guest's calendar (tour bookings excluded)
    @Query("""
            select b from Booking b
            where b.listing.id = :listingId
              and b.tourDate is null
              and b.status in :statuses
              and b.startDate <= :to
              and :from <= b.endDate
            """)
    List<Booking> findOverlapping(@Param("listingId") Long listingId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    // scheduler: bookings whose expiresAt has passed (statuses = BookingStatus.EXPIRABLE)
    List<Booking> findByStatusInAndExpiresAtBefore(Collection<BookingStatus> statuses, Instant now);

    // scheduler: CONFIRMED bookings whose last day has passed -> COMPLETED
    List<Booking> findByStatusAndEndDateBefore(BookingStatus status, LocalDate today);

    // any booking (also cancelled / expired) on a tour date: then the date is cancelled instead of deleted
    boolean existsByTourDateId(Long tourDateId);

    List<Booking> findByListingIdAndStatusIn(Long listingId, Collection<BookingStatus> statuses);

    List<Booking> findByTourDateIdAndStatusIn(Long tourDateId, Collection<BookingStatus> statuses);
}
