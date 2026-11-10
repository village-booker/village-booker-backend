package com.villagebooker.service.notification;

import com.villagebooker.enums.listing.ListingStatus;

public record ListingStatusChangedEvent(Long listingId, ListingStatus from, ListingStatus to) {
}
