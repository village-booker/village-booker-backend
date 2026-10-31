package com.villagebooker.dto.booking;

import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.model.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// closed interval [startDate, endDate]; departureDate = endDate + 1 for accommodation (last night + 1), null for a tour
public record BookingResponse(
        Long id,
        BookingStatus status,
        Long listingId,
        ListingType listingType,
        String listingName,
        Long tourDateId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate departureDate,
        Integer guestCount,
        BigDecimal totalPrice,
        BigDecimal amountDue,
        CancellationPolicy cancellationPolicy,
        Instant freeCancellationUntil,
        BigDecimal refundPercentage,
        BigDecimal depositPercentage,
        PaymentMethod paymentMethod,
        String note,
        Instant expiresAt,
        Instant createdAt,
        Instant cancelledAt,
        String cancellationReason
) {

    public static BookingResponse from(Booking booking) {
        boolean tour = booking.getTourDate() != null;
        return new BookingResponse(booking.getId(), booking.getStatus(), booking.getListing().getId(),
                tour ? ListingType.TOUR : ListingType.ACCOMMODATION, booking.getListing().getName(),
                tour ? booking.getTourDate().getId() : null,
                booking.getStartDate(), booking.getEndDate(), tour ? null : booking.getEndDate().plusDays(1),
                booking.getGuestCount(), booking.getTotalPrice(), booking.getAmountDue(),
                booking.getCancellationPolicy(), booking.getFreeCancellationUntil(), booking.getRefundPercentage(),
                booking.getDepositPercentage(), booking.getPaymentMethod(), booking.getNote(),
                booking.getExpiresAt(), booking.getCreatedAt(), booking.getCancelledAt(),
                booking.getCancellationReason());
    }
}
