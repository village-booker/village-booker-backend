package com.villagebooker.dto.listing;

import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;

import java.math.BigDecimal;

// One row of a listing list. price = basePricePerNight (ACCOMMODATION, per night) or pricePerPerson (TOUR).
public record ListingSummaryResponse(
        Long id,
        ListingType type,
        ListingStatus status,
        String name,
        String place,
        String district,
        Integer minGuests,
        Integer maxGuests,
        BigDecimal price,
        BigDecimal rating,
        Integer reviewCount
) {

    public static ListingSummaryResponse from(Listing listing) {
        ListingType type;
        BigDecimal price;
        switch (listing) {
            case Accommodation a -> {
                type = ListingType.ACCOMMODATION;
                price = a.getBasePricePerNight();
            }
            case Tour t -> {
                type = ListingType.TOUR;
                price = t.getPricePerPerson();
            }
            default -> throw new IllegalStateException("Unknown listing type: " + listing.getClass());
        }
        return new ListingSummaryResponse(listing.getId(), type, listing.getStatus(), listing.getName(),
                listing.getPlace(), listing.getDistrict(), listing.getMinGuests(), listing.getMaxGuests(),
                price, listing.getRating(), listing.getReviewCount());
    }
}
