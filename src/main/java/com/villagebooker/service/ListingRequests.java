package com.villagebooker.service;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.dto.listing.AccommodationRequest;
import com.villagebooker.dto.listing.ListingRequest;
import com.villagebooker.dto.listing.TourRequest;
import com.villagebooker.enums.PaymentMethod;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;

import java.math.BigDecimal;
import java.util.Map;

// Business rules and request -> entity copying shared by creating, editing and change requests of listings.
// Single-field limits are Bean Validation on the request; these rules span several fields.
final class ListingRequests {

    private ListingRequests() {
    }

    static void check(AccommodationRequest request) {
        checkCommon(request);
        if (request.baseGuests() < request.minGuests() || request.baseGuests() > request.maxGuests()) {
            throw new BadRequestException(ErrorCode.BASE_GUESTS_OUT_OF_RANGE,
                    "baseGuests must be between minGuests and maxGuests.",
                    Map.of("minGuests", request.minGuests(), "maxGuests", request.maxGuests()));
        }
    }

    static void check(TourRequest request) {
        checkCommon(request);
    }

    static void copy(AccommodationRequest request, Accommodation accommodation) {
        copyCommon(request, accommodation);
        accommodation.setBasePricePerNight(request.basePricePerNight());
        accommodation.setBaseGuests(request.baseGuests());
        accommodation.setExtraGuestPrice(request.extraGuestPrice());
        accommodation.setDoubleBedCount(request.doubleBedCount());
        accommodation.setSingleBedCount(request.singleBedCount());
        accommodation.setRoomCount(request.roomCount());
    }

    static void copy(TourRequest request, Tour tour) {
        copyCommon(request, tour);
        tour.setDurationDays(request.durationDays());
        tour.setDifficulty(request.difficulty());
        tour.setPricePerPerson(request.pricePerPerson());
    }

    private static void checkCommon(ListingRequest request) {
        if (request.minGuests() > request.maxGuests()) {
            throw new BadRequestException(ErrorCode.GUEST_RANGE_INVALID, "minGuests must not be greater than maxGuests.");
        }

        // cancellation terms must match the policy (refund table in spec section 3)
        switch (request.cancellationPolicy()) {
            // free until N days before arrival, nothing afterwards
            case FREE -> {
                if (request.freeCancellationDays() == null) {
                    throw new BadRequestException(ErrorCode.FREE_CANCELLATION_DAYS_REQUIRED,
                            "FREE cancellation policy needs freeCancellationDays.");
                }
                forbidRefundPercentage(request);
            }
            // free until N days before arrival (optional), refundPercentage afterwards
            case PARTIAL -> {
                if (request.refundPercentage() == null) {
                    throw new BadRequestException(ErrorCode.REFUND_PERCENTAGE_REQUIRED,
                            "PARTIAL cancellation policy needs refundPercentage.");
                }
            }
            // no refund at all
            case NON_REFUNDABLE -> {
                if (request.freeCancellationDays() != null) {
                    throw new BadRequestException(ErrorCode.FREE_CANCELLATION_DAYS_NOT_ALLOWED,
                            "NON_REFUNDABLE cancellation policy has no free cancellation period.");
                }
                forbidRefundPercentage(request);
            }
        }

        // a deposit is paid online, so it makes no sense when everything is paid on site
        if (request.paymentMethod() == PaymentMethod.ON_SITE
                && request.depositPercentage() != null
                && request.depositPercentage().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException(ErrorCode.DEPOSIT_NOT_ALLOWED_FOR_ON_SITE,
                    "A deposit needs ONLINE or COMBINED payment method.");
        }
    }

    private static void forbidRefundPercentage(ListingRequest request) {
        if (request.refundPercentage() != null) {
            throw new BadRequestException(ErrorCode.REFUND_PERCENTAGE_NOT_ALLOWED,
                    "refundPercentage is only used with PARTIAL cancellation policy.");
        }
    }

    private static void copyCommon(ListingRequest request, Listing listing) {
        listing.setName(request.name().trim());
        listing.setDescription(blankToNull(request.description()));
        listing.setRequiresOwnerApproval(request.requiresOwnerApproval());
        listing.setMinGuests(request.minGuests());
        listing.setMaxGuests(request.maxGuests());
        listing.setAddress(blankToNull(request.address()));
        listing.setPlace(request.place().trim());
        listing.setMunicipality(request.municipality().trim());
        listing.setDistrict(request.district().trim());
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setCancellationPolicy(request.cancellationPolicy());
        listing.setPaymentMethod(request.paymentMethod());
        listing.setDepositPercentage(request.depositPercentage());
        listing.setFreeCancellationDays(request.freeCancellationDays());
        listing.setRefundPercentage(request.refundPercentage());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
