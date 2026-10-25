package com.villagebooker.dto.listing;

import com.villagebooker.enums.CancellationPolicy;
import com.villagebooker.enums.ListingStatus;
import com.villagebooker.enums.ListingType;
import com.villagebooker.enums.PaymentMethod;
import com.villagebooker.enums.TourDifficulty;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;

import java.math.BigDecimal;

// Full listing. Exactly one of accommodation / tour is filled, matching type.
public record ListingResponse(
        Long id,
        ListingType type,
        ListingStatus status,
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
        AccommodationDetails accommodation,
        TourDetails tour
) {

    public record AccommodationDetails(
            BigDecimal basePricePerNight,
            Integer baseGuests,
            BigDecimal extraGuestPrice,
            Integer doubleBedCount,
            Integer singleBedCount,
            Integer roomCount
    ) {
    }

    public record TourDetails(
            Integer durationDays,
            TourDifficulty difficulty,
            BigDecimal pricePerPerson
    ) {
    }

    public static ListingResponse from(Listing listing) {
        AccommodationDetails accommodation = null;
        TourDetails tour = null;
        ListingType type;
        switch (listing) {
            case Accommodation a -> {
                type = ListingType.ACCOMMODATION;
                accommodation = new AccommodationDetails(a.getBasePricePerNight(), a.getBaseGuests(),
                        a.getExtraGuestPrice(), a.getDoubleBedCount(), a.getSingleBedCount(), a.getRoomCount());
            }
            case Tour t -> {
                type = ListingType.TOUR;
                tour = new TourDetails(t.getDurationDays(), t.getDifficulty(), t.getPricePerPerson());
            }
            default -> throw new IllegalStateException("Unknown listing type: " + listing.getClass());
        }
        return new ListingResponse(
                listing.getId(),
                type,
                listing.getStatus(),
                listing.getName(),
                listing.getDescription(),
                listing.isRequiresOwnerApproval(),
                listing.getMinGuests(),
                listing.getMaxGuests(),
                listing.getAddress(),
                listing.getPlace(),
                listing.getMunicipality(),
                listing.getDistrict(),
                listing.getLatitude(),
                listing.getLongitude(),
                listing.getCancellationPolicy(),
                listing.getPaymentMethod(),
                listing.getDepositPercentage(),
                listing.getFreeCancellationDays(),
                listing.getRefundPercentage(),
                listing.getRating(),
                listing.getReviewCount(),
                accommodation,
                tour);
    }
}
