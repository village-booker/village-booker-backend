package com.villagebooker.service.booking;

import com.villagebooker.dto.booking.BookingRejectionRequest;
import com.villagebooker.dto.booking.OwnerBookingResponse;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// Bookings of the host's listings. A booking of another host's listing is 404, like another guest's booking.
@Service
@RequiredArgsConstructor
public class OwnerBookingService {

    private final BookingRepo bookingRepo;
    private final TourDateRepo tourDateRepo;
    private final BookingStatusChanger statusChanger;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<OwnerBookingResponse> findMine(Long ownerId, BookingStatus status, Long listingId,
                                                       Pageable pageable) {
        Specification<Booking> spec = (b, q, cb) -> cb.equal(b.get("listing").get("owner").get("id"), ownerId);
        if (status != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("status"), status));
        }
        if (listingId != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("listing").get("id"), listingId));
        }
        return PageResponse.of(bookingRepo.findAll(spec, pageable), OwnerBookingResponse::from);
    }

    @Transactional(readOnly = true)
    public OwnerBookingResponse getMine(Long ownerId, Long bookingId) {
        return OwnerBookingResponse.from(findOwn(ownerId, bookingId));
    }

    // dates / places stay taken: the booking already holds them since it was created
    @Transactional
    public OwnerBookingResponse approve(Long ownerId, Long bookingId) {
        Booking booking = findOwn(ownerId, bookingId);
        BookingStatus target = BookingTerms.afterApproval(booking);
        BookingTransitions.check(booking.getStatus(), target);
        statusChanger.requireNotExpired(booking);
        statusChanger.moveTo(booking, target);
        return OwnerBookingResponse.from(booking);
    }

    @Transactional
    public OwnerBookingResponse reject(Long ownerId, Long bookingId, BookingRejectionRequest request) {
        Booking booking = findOwn(ownerId, bookingId);
        statusChanger.moveTo(booking, BookingStatus.REJECTED);
        String reason = request == null ? null : request.reason();
        booking.setCancellationReason(reason == null || reason.isBlank() ? null : reason.trim());
        if (booking.getTourDate() != null) {
            releasePlaces(booking);
        }
        return OwnerBookingResponse.from(booking);
    }

    // the tour date is only a lazy proxy until here, so the locking query loads its current bookedCount
    private void releasePlaces(Booking booking) {
        TourDate date = tourDateRepo.findByIdForUpdate(booking.getTourDate().getId()).orElseThrow();
        TourDateSeats.release(date, booking.getGuestCount(), Instant.now(clock));
    }

    private Booking findOwn(Long ownerId, Long bookingId) {
        return bookingRepo.findByIdAndListingOwnerId(bookingId, ownerId)
                .orElseThrow(() -> BookingService.bookingNotFound(bookingId));
    }
}
