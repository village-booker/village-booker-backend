package com.villagebooker.service.listing;

import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Tour;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Price formulas of spec 1.3, shared by the search (and later by booking creation)
public final class ListingPrices {

    private ListingPrices() {
    }

    public static BigDecimal accommodationPerNight(Accommodation accommodation, int guests) {
        int extraGuests = Math.max(0, guests - accommodation.getBaseGuests());
        return accommodation.getBasePricePerNight()
                .add(accommodation.getExtraGuestPrice().multiply(BigDecimal.valueOf(extraGuests)));
    }

    public static BigDecimal accommodationTotal(Accommodation accommodation, int guests, LocalDate arrival,
                                                LocalDate departure) {
        long nights = ChronoUnit.DAYS.between(arrival, departure);
        return accommodationPerNight(accommodation, guests).multiply(BigDecimal.valueOf(nights));
    }

    public static BigDecimal tourTotal(Tour tour, int guests) {
        return tour.getPricePerPerson().multiply(BigDecimal.valueOf(guests));
    }
}
