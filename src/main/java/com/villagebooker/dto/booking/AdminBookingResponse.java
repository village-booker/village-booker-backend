package com.villagebooker.dto.booking;

import com.villagebooker.dto.user.ContactResponse;
import com.villagebooker.model.Booking;

public record AdminBookingResponse(
        BookingResponse booking,
        ContactResponse guest,
        ContactResponse owner
) {

    public static AdminBookingResponse from(Booking booking) {
        return new AdminBookingResponse(BookingResponse.from(booking), ContactResponse.from(booking.getGuest()),
                ContactResponse.from(booking.getListing().getOwner()));
    }
}
