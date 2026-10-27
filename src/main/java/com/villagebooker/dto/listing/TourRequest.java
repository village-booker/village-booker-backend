package com.villagebooker.dto.listing;

import com.villagebooker.enums.TourDifficulty;
import com.villagebooker.enums.CancellationPolicy;
import com.villagebooker.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// create and update of a tour; minGuests / maxGuests of a tour apply to a whole tour date (all groups together, spec 5.1)
public record TourRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        boolean requiresOwnerApproval,
        @NotNull @Min(1) @Max(1000) Integer minGuests,
        @NotNull @Min(1) @Max(1000) Integer maxGuests,
        @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String place,
        @NotBlank @Size(max = 100) String municipality,
        @NotBlank @Size(max = 100) String district,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @NotNull CancellationPolicy cancellationPolicy,
        @NotNull PaymentMethod paymentMethod,
        // percentages 0..100; which of them are required depends on the policy / payment method (ListingService)
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal depositPercentage,
        @Min(0) @Max(365) Integer freeCancellationDays,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal refundPercentage,
        @NotNull @Min(1) @Max(60) Integer durationDays,
        @NotNull TourDifficulty difficulty,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal pricePerPerson
) implements ListingRequest {
}
