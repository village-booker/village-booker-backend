package com.villagebooker.dto.listing;

import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.model.ListingImage;

import java.time.Instant;

public record AdminImageResponse(
        Long id,
        String url,
        ImageStatus status,
        String rejectionReason,
        Long listingId,
        String listingName,
        Instant createdAt
) {

    public static AdminImageResponse from(ListingImage image) {
        return new AdminImageResponse(image.getId(), image.getUrl(), image.getStatus(), image.getRejectionReason(),
                image.getListing().getId(), image.getListing().getName(), image.getCreatedAt());
    }
}
