package com.villagebooker.repo;

import com.villagebooker.enums.BookingStatus;
import com.villagebooker.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepo extends JpaRepository<Booking, Long> {

    // listing (and guest) loaded in the same query, otherwise mapping to DTO makes N+1 queries
    @EntityGraph(attributePaths = "listing")
    Page<Booking> findByGuestId(Long guestId, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "guest"})
    Page<Booking> findByListingOwnerId(Long ownerId, Pageable pageable);

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

    // scheduler: bookings whose expiresAt has passed (statuses = BookingStatus.EXPIRABLE)
    List<Booking> findByStatusInAndExpiresAtBefore(Collection<BookingStatus> statuses, Instant now);

    // scheduler: CONFIRMED bookings whose last day has passed -> COMPLETED
    List<Booking> findByStatusAndEndDateBefore(BookingStatus status, LocalDate today);

    // active bookings of a listing, e.g. before deleting it (spec 9.3)
    List<Booking> findByListingIdAndStatusIn(Long listingId, Collection<BookingStatus> statuses);

    // active bookings of a tour date, e.g. when it is cancelled
    List<Booking> findByTourDateIdAndStatusIn(Long tourDateId, Collection<BookingStatus> statuses);
}
