package com.villagebooker.dto.listing;

import com.villagebooker.enums.listing.ListingType;

import java.math.BigDecimal;

// One result of the public search.
// price: per night (ACCOMMODATION) or per person (TOUR); totalPrice only when the search had enough data
// (accommodation: dates; tour: guests), see ListingPrices.
public record ListingCardResponse(
        Long id,
        ListingType type,
        String name,
        String place,
        String municipality,
        String district,
        Integer minGuests,
        Integer maxGuests,
        BigDecimal price,
        BigDecimal totalPrice,
        BigDecimal rating,
        Integer reviewCount,
        String mainImageUrl
) {
}
