package com.villagebooker.dto.listing;

import com.villagebooker.dto.feature.FeatureResponse;
import com.villagebooker.dto.tour.TourDateResponse;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.enums.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// What a guest sees on a listing page: no moderation data, only APPROVED images, the host without contact details.
// bookable = false for a listing paused by its owner (visible to guests who already booked it, but no new bookings).
// tourDates: upcoming dates with free places (TOUR only, otherwise empty).
public record PublicListingResponse(
        Long id,
        ListingType type,
        boolean bookable,
        String name,
        String description,
        boolean requiresOwnerApproval,
        Integer minGuests,
        Integer maxGuests,
        String address,
        String place,
        String municipality,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        CancellationPolicy cancellationPolicy,
        PaymentMethod paymentMethod,
        BigDecimal depositPercentage,
        Integer freeCancellationDays,
        BigDecimal refundPercentage,
        BigDecimal rating,
        Integer reviewCount,
        ListingResponse.AccommodationDetails accommodation,
        ListingResponse.TourDetails tour,
        List<ListingImageResponse> images,
        List<FeatureResponse> features,
        Host host,
        List<TourDateResponse> tourDates
) {

    public record Host(Long id, String firstName, String imageUrl, Instant memberSince) {
    }
}
