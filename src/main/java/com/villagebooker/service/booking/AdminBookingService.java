package com.villagebooker.service.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.booking.AdminBookingDetailResponse;
import com.villagebooker.dto.booking.AdminBookingResponse;
import com.villagebooker.dto.booking.AdminCancellationRequest;
import com.villagebooker.dto.booking.CancellationResponse;
import com.villagebooker.dto.booking.OwnerCancellationStatsResponse;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.payment.PaymentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

// The admin sees every booking and how often hosts cancel (spec 9.4, penalties by hand) and cancels a booking
// that the guest and the host cannot sort out themselves (a stay that has begun, a blocked host).
@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingRepo bookingRepo;
    private final PaymentRepo paymentRepo;
    private final BookingCanceller canceller;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminBookingResponse> findAll(BookingStatus status, Long listingId, Long guestId,
                                                      Long ownerId, Pageable pageable) {
        Specification<Booking> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("status"), status));
        }
        if (listingId != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("listing").get("id"), listingId));
        }
        if (guestId != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("guest").get("id"), guestId));
        }
        if (ownerId != null) {
            spec = spec.and((b, q, cb) -> cb.equal(b.get("listing").get("owner").get("id"), ownerId));
        }
        return PageResponse.of(bookingRepo.findAll(spec, pageable), AdminBookingResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminBookingDetailResponse get(Long bookingId) {
        Booking booking = bookingRepo.findWithPartiesById(bookingId)
                .orElseThrow(() -> BookingService.bookingNotFound(bookingId));
        return AdminBookingDetailResponse.from(booking, paymentRepo.findByBookingIdOrderById(bookingId));
    }

    @Transactional
    public CancellationResponse cancel(Long bookingId, AdminCancellationRequest request) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow(() -> BookingService.bookingNotFound(bookingId));
        return canceller.cancelByAdmin(booking, request.reason(), request.refundPercentage());
    }

    // pageable without a sort: the query orders by the number of cancellations
    @Transactional(readOnly = true)
    public PageResponse<OwnerCancellationStatsResponse> ownerCancellations(int months, Pageable pageable) {
        Instant since = AppTime.startOfDay(AppTime.today(clock).minusMonths(months));
        return PageResponse.of(bookingRepo.countOwnerCancellationsSince(since, pageable), s -> s);
    }
}
