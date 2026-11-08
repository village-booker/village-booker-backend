package com.villagebooker.dto.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// the host sees the reason on the listing (removalReason)
public record ListingRemovalRequest(@NotBlank @Size(max = 1000) String reason) {
}
