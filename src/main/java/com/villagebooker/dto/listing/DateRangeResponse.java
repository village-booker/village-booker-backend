package com.villagebooker.dto.listing;

import java.time.LocalDate;

// closed interval: both days included
public record DateRangeResponse(LocalDate startDate, LocalDate endDate) {
}
