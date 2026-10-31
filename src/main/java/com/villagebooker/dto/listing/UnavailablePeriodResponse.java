package com.villagebooker.dto.listing;

import com.villagebooker.model.UnavailablePeriod;

import java.time.LocalDate;

public record UnavailablePeriodResponse(Long id, LocalDate startDate, LocalDate endDate, String reason) {

    public static UnavailablePeriodResponse from(UnavailablePeriod period) {
        return new UnavailablePeriodResponse(period.getId(), period.getStartDate(), period.getEndDate(),
                period.getReason());
    }
}
