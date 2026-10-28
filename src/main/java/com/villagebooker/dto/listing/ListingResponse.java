package com.villagebooker.dto.listing;

import com.villagebooker.dto.feature.FeatureResponse;
import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.listing.ListingType;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.enums.listing.TourDifficulty;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Full listing. Exactly one of accommodation / tour is filled, matching type.
// Reads images and features (lazy), so it is built inside a transaction.
public record ListingResponse(
        Long id,
        ListingType type,
        ListingStatus status,
        // filled only while status = REJECTED
        String rejectionReason,
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
        TourDetails tour,
        // ordered by sortOrder, the first one is the main image; the owner also sees PENDING / REJECTED ones
        List<ListingImageResponse> images,
        List<FeatureResponse> features
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
                listing.getRejectionReason(),
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
                tour,
                listing.getImages().stream()
                        .sorted(Comparator.comparing(i -> i.getSortOrder()))
                        .map(ListingImageResponse::from)
                        .toList(),
                listing.getFeatures().stream()
                        .sorted(Comparator.comparing(f -> f.getNameSr()))
                        .map(FeatureResponse::from)
                        .toList());
    }
}
