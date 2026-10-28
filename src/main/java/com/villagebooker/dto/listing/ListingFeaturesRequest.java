package com.villagebooker.dto.listing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// the full set of features of a listing (replaces the old one); [] removes all
public record ListingFeaturesRequest(@NotNull @Size(max = 50) List<@NotNull Long> featureIds) {
}
