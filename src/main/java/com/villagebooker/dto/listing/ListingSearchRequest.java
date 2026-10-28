package com.villagebooker.dto.listing;

import com.villagebooker.enums.listing.ListingSort;
import com.villagebooker.enums.listing.ListingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Query parameters of the public search; every filter is optional.
// dateFrom / dateTo: accommodation = arrival / departure (nights dateFrom..dateTo-1 must be free),
// tour = a bookable date must start between dateFrom and dateTo (both included).
// minPrice / maxPrice and sort PRICE_* need a type (price per night vs price per person).
public record ListingSearchRequest(
        @Size(max = 100) String q,
        ListingType type,
        @Size(max = 100) String district,
        @Min(1) @Max(1000) Integer guests,
        LocalDate dateFrom,
        LocalDate dateTo,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice,
        @Size(max = 20) List<Long> featureIds,
        ListingSort sort
) {
}
