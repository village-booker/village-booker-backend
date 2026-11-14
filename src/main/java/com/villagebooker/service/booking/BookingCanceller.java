package com.villagebooker.service.booking;

import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.time.AppTime;
import com.villagebooker.dto.booking.BookingResponse;
import com.villagebooker.dto.booking.CancellationResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.payment.PaymentStatus;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Payment;
import com.villagebooker.model.TourDate;
import com.villagebooker.repo.payment.PaymentRepo;
import com.villagebooker.repo.tour.TourDateRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Cancelling one booking (spec 3, 9.2): status, cancelledAt, reason, the refund of what was paid and, for a tour,
// the places on its date.
@Component
@RequiredArgsConstructor
public class BookingCanceller {

    private final PaymentRepo paymentRepo;
    private final TourDateRepo tourDateRepo;
    private final BookingStatusChanger statusChanger;
    private final Clock clock;

    public CancellationResponse preview(Booking booking, BookingStatus status) {
        BookingTransitions.check(booking.getStatus(), status);
        requireNotStarted(booking);
        BigDecimal paid = paid(succeededPayments(booking));
        BigDecimal refund = Refunds.amount(paid, Refunds.percentage(booking, status, Instant.now(clock)));
        return new CancellationResponse(BookingResponse.from(booking), paid, refund);
    }

    // CANCELLED_MIN_NOT_REACHED comes from the scheduler, which can run on the first day (deadline 0 days before)
    public CancellationResponse cancel(Booking booking, BookingStatus status, String reason) {
        BookingTransitions.check(booking.getStatus(), status);
        if (status != BookingStatus.CANCELLED_MIN_NOT_REACHED) {
            requireNotStarted(booking);
        }
        return cancel(booking, status, reason, Refunds.percentage(booking, status, Instant.now(clock)));
    }

    // the admin also cancels a stay or tour that has begun, and decides the refund instead of the policy table
    public CancellationResponse cancelByAdmin(Booking booking, String reason, BigDecimal refundPercentage) {
        BookingTransitions.check(booking.getStatus(), BookingStatus.CANCELLED_BY_ADMIN);
        return cancel(booking, BookingStatus.CANCELLED_BY_ADMIN, reason, refundPercentage);
    }

    private CancellationResponse cancel(Booking booking, BookingStatus status, String reason, BigDecimal percentage) {
        Instant now = Instant.now(clock);
        statusChanger.moveTo(booking, status);
        booking.setCancelledAt(now);
        booking.setCancellationReason(reason == null || reason.isBlank() ? null : reason.trim());
        if (booking.getTourDate() != null) {
            releasePlaces(booking);
        }

        List<Payment> payments = succeededPayments(booking);
        BigDecimal paid = paid(payments);
        BigDecimal refund = Refunds.amount(paid, percentage);
        refundPayments(payments, refund);
        return new CancellationResponse(BookingResponse.from(booking), paid, refund);
    }

    // The tour date is only a lazy proxy until here, so the locking query loads its current bookedCount. A caller
    // that already holds the lock gets the same (current) instance back.
    void releasePlaces(Booking booking) {
        releasePlaces(booking, Instant.now(clock));
    }

    void releasePlaces(Booking booking, Instant at) {
        TourDate date = tourDateRepo.findByIdForUpdate(booking.getTourDate().getId()).orElseThrow();
        TourDateSeats.release(date, booking.getGuestCount(), at);
    }

    // a stay or tour that has begun is not cancelled through the app (that is for the admin to sort out)
    public void requireNotStarted(Booking booking) {
        if (!AppTime.today(clock).isBefore(booking.getStartDate())) {
            throw new ConflictException(ErrorCode.BOOKING_ALREADY_STARTED,
                    "Booking " + booking.getId() + " has already started.",
                    Map.of("startDate", booking.getStartDate().toString()));
        }
    }

    // No payment provider yet, so the refund is only recorded here; with one it is sent after the commit (spec 9.2).
    // The amount is spread over the payments in order: partial when only part of one payment goes back.
    private static void refundPayments(List<Payment> payments, BigDecimal refund) {
        BigDecimal left = refund;
        for (Payment payment : payments) {
            if (left.signum() == 0) {
                return;
            }
            BigDecimal part = left.min(payment.getAmount());
            payment.setRefundAmount(part);
            payment.setStatus(PaymentStatus.REFUNDED);
            left = left.subtract(part);
        }
    }

    private List<Payment> succeededPayments(Booking booking) {
        return paymentRepo.findByBookingId(booking.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .sorted(Comparator.comparing(Payment::getId))
                .toList();
    }

    private static BigDecimal paid(List<Payment> payments) {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
