package com.villagebooker.dto.listing;

import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.model.ListingImage;

// sortOrder 0 is the main image; rejectionReason only when status = REJECTED (the url no longer works then)
public record ListingImageResponse(Long id, String url, Integer sortOrder, ImageStatus status, String rejectionReason) {

    public static ListingImageResponse from(ListingImage image) {
        return new ListingImageResponse(image.getId(), image.getUrl(), image.getSortOrder(), image.getStatus(),
                image.getRejectionReason());
    }
}
