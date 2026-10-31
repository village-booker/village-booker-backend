package com.villagebooker.service.booking;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Snapshot of the listing's terms on a new booking (spec 1.1). Copied only here, never from the request, and
// never changed afterward: cancellation and refunds read the booking, so later listing edits do not affect it.
final class BookingTerms {

    private BookingTerms() {
    }

    static void copy(Listing listing, Booking booking, BigDecimal totalPrice) {
        booking.setTotalPrice(totalPrice);
        booking.setAmountDue(totalPrice);
        booking.setCancellationPolicy(listing.getCancellationPolicy());
        booking.setFreeCancellationUntil(freeCancellationUntil(listing, booking.getStartDate()));
        booking.setRefundPercentage(listing.getRefundPercentage());
        booking.setDepositPercentage(listing.getDepositPercentage());
        booking.setPaymentMethod(listing.getPaymentMethod());
    }

    // can be in the past (booked after the deadline): then there is no free cancellation
    static Instant freeCancellationUntil(Listing listing, LocalDate startDate) {
        if (listing.getCancellationPolicy() == CancellationPolicy.NON_REFUNDABLE
                || listing.getFreeCancellationDays() == null) {
            return null;
        }
        return AppTime.startOfDay(startDate.minusDays(listing.getFreeCancellationDays()));
    }

    // spec 1: deposit flow only for online payment with a percentage above 0
    static boolean hasDeposit(Booking booking) {
        return booking.getPaymentMethod() != PaymentMethod.ON_SITE
                && booking.getDepositPercentage() != null
                && booking.getDepositPercentage().signum() > 0;
    }

    // spec 3: manual approval first; then the deposit, if there is one; otherwise confirmed right away
    static BookingStatus initialStatus(Listing listing, Booking booking) {
        if (listing.isRequiresOwnerApproval()) {
            return BookingStatus.AWAITING_OWNER_APPROVAL;
        }
        return hasDeposit(booking) ? BookingStatus.AWAITING_PAYMENT : BookingStatus.CONFIRMED;
    }
}
