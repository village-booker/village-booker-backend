package com.villagebooker.dto.user;

import com.villagebooker.dto.listing.ListingCardResponse;

import java.time.Instant;

// bookable = false while the host has paused the listing or is blocked
public record FavoriteResponse(
        ListingCardResponse listing,
        boolean bookable,
        Instant addedAt
) {
}
