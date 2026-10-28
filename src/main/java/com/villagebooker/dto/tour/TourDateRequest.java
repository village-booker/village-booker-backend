package com.villagebooker.dto.tour;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// A new date of a tour. endDate is computed (startDate + tour.durationDays - 1).
// minGuestsDeadlineDaysBefore: how many days before startDate the minimum number of guests must be reached;
// the deadline is midnight (Europe/Belgrade) at the start of that day, 0 = midnight before the first tour day.
public record TourDateRequest(
        @NotNull LocalDate startDate,
        @NotNull @Min(0) @Max(30) Integer minGuestsDeadlineDaysBefore
) {
}
