package com.villagebooker.dto.listing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// nights the owner blocks, closed interval [startDate, endDate]; reason is seen only by the owner
public record UnavailablePeriodRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 255) String reason
) {
}
