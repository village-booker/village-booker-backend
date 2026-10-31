package com.villagebooker.dto.booking;

import com.villagebooker.model.Booking;
import com.villagebooker.model.User;

// what the host sees: the booking and who made it
public record OwnerBookingResponse(
        BookingResponse booking,
        Guest guest
) {

    public record Guest(Long id, String firstName, String lastName, String email, String phone) {
    }

    public static OwnerBookingResponse from(Booking booking) {
        User guest = booking.getGuest();
        return new OwnerBookingResponse(
                BookingResponse.from(booking),
                new Guest(guest.getId(), guest.getFirstName(), guest.getLastName(), guest.getEmail(), guest.getPhone()));
    }
}
