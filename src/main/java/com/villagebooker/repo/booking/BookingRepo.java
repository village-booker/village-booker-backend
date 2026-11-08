package com.villagebooker.repo.booking;

import com.villagebooker.dto.booking.OwnerCancellationStatsResponse;
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

// JpaSpecificationExecutor: the owner's and the admin's lists with optional filters (status, listing, guest, owner)
public interface BookingRepo extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    // listing (and guest) loaded in the same query, otherwise mapping to DTO makes N+1 queries
    @EntityGraph(attributePaths = "listing")
    Page<Booking> findByGuestId(Long guestId, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "guest"})
    Page<Booking> findByListingOwnerId(Long ownerId, Pageable pageable);

    // listing.owner for the admin's list (for the host's own list it is the host, one row)
    @Override
    @EntityGraph(attributePaths = {"listing", "listing.owner", "guest"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "listing.owner", "guest"})
    Optional<Booking> findWithPartiesById(Long id);

    @EntityGraph(attributePaths = "listing")
    Optional<Booking> findByIdAndGuestId(Long id, Long guestId);

    @EntityGraph(attributePaths = {"listing", "guest"})
    Optional<Booking> findByIdAndListingOwnerId(Long id, Long ownerId);

    // only the id, so the listing lock is the first load of the listing in the transaction
    @Query("select b.listing.id from Booking b where b.id = :id and b.guest.id = :guestId")
    Optional<Long> findListingIdByIdAndGuestId(@Param("id") Long id, @Param("guestId") Long guestId);

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
    @Query("select b.id from Booking b where b.status in :statuses and b.expiresAt < :now order by b.id")
    List<Long> findIdsToExpire(@Param("statuses") Collection<BookingStatus> statuses, @Param("now") Instant now);

    // scheduler: CONFIRMED bookings whose last day has passed -> COMPLETED
    @Query("select b.id from Booking b where b.status = :status and b.endDate < :today order by b.id")
    List<Long> findIdsEndedBefore(@Param("status") BookingStatus status, @Param("today") LocalDate today);

    // any booking (also cancelled / expired) on a tour date: then the date is cancelled instead of deleted
    boolean existsByTourDateId(Long tourDateId);

    // bookings of a listing that have not begun yet (deleting the listing cancels them)
    List<Booking> findByListingIdAndStatusInAndStartDateAfterOrderByStartDate(
            Long listingId, Collection<BookingStatus> statuses, LocalDate today);

    long countByGuestId(Long guestId);

    long countByListingOwnerIdAndStatus(Long ownerId, BookingStatus status);

    // spec 9.4: hosts by the number of their cancellations since a moment, most first (penalties are decided by hand)
    @Query(value = """
            select new com.villagebooker.dto.booking.OwnerCancellationStatsResponse(
                o.id, o.firstName, o.lastName, o.email, count(b), max(b.cancelledAt))
            from Booking b join b.listing l join l.owner o
            where b.status = com.villagebooker.enums.booking.BookingStatus.CANCELLED_BY_OWNER
              and b.cancelledAt >= :since
            group by o.id, o.firstName, o.lastName, o.email
            order by count(b) desc, o.id
            """,
            countQuery = """
            select count(distinct b.listing.owner.id) from Booking b
            where b.status = com.villagebooker.enums.booking.BookingStatus.CANCELLED_BY_OWNER
              and b.cancelledAt >= :since
            """)
    Page<OwnerCancellationStatsResponse> countOwnerCancellationsSince(@Param("since") Instant since, Pageable pageable);

    List<Booking> findByTourDateIdAndStatusIn(Long tourDateId, Collection<BookingStatus> statuses);
}
