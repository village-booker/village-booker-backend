package com.villagebooker.dto.booking;

import com.villagebooker.dto.payment.PaymentResponse;
import com.villagebooker.dto.user.ContactResponse;
import com.villagebooker.model.Booking;
import com.villagebooker.model.Payment;

import java.util.List;

public record AdminBookingDetailResponse(
        BookingResponse booking,
        ContactResponse guest,
        ContactResponse owner,
        List<PaymentResponse> payments
) {

    public static AdminBookingDetailResponse from(Booking booking, List<Payment> payments) {
        return new AdminBookingDetailResponse(BookingResponse.from(booking), ContactResponse.from(booking.getGuest()),
                ContactResponse.from(booking.getListing().getOwner()),
                payments.stream().map(PaymentResponse::from).toList());
    }
}
