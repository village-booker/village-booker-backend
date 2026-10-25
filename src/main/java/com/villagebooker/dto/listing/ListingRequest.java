package com.villagebooker.dto.listing;

import com.villagebooker.enums.CancellationPolicy;
import com.villagebooker.enums.PaymentMethod;

import java.math.BigDecimal;

// Fields shared by accommodation and tour requests. The records stay flat (frontend sends one JSON object)
// and implement this, so ListingService checks the shared business rules once.
public interface ListingRequest {

    String name();

    String description();

    boolean requiresOwnerApproval();

    Integer minGuests();

    Integer maxGuests();

    String address();

    String place();

    String municipality();

    String district();

    BigDecimal latitude();

    BigDecimal longitude();

    CancellationPolicy cancellationPolicy();

    PaymentMethod paymentMethod();

    BigDecimal depositPercentage();

    Integer freeCancellationDays();

    BigDecimal refundPercentage();
}
