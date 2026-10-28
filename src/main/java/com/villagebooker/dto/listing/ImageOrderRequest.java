package com.villagebooker.dto.listing;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// all image ids of the listing in the new order; the first one becomes the main image
public record ImageOrderRequest(@NotEmpty List<@NotNull Long> imageIds) {
}
