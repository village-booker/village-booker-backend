package com.villagebooker.dto.tour;

import com.villagebooker.enums.tour.TourDateStatus;
import com.villagebooker.model.TourDate;

import java.time.Instant;
import java.time.LocalDate;

// closed interval [startDate, endDate]; freePlaces = tour.maxGuests - bookedCount
public record TourDateResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        TourDateStatus status,
        Integer bookedCount,
        Integer freePlaces,
        Instant minGuestsDeadline
) {

    public static TourDateResponse from(TourDate date) {
        return new TourDateResponse(date.getId(), date.getStartDate(), date.getEndDate(), date.getStatus(),
                date.getBookedCount(), date.getTour().getMaxGuests() - date.getBookedCount(),
                date.getMinGuestsDeadline());
    }
}
