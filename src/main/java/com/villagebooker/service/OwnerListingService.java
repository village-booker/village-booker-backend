package com.villagebooker.service;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.CreateAccommodationRequest;
import com.villagebooker.dto.listing.CreateTourRequest;
import com.villagebooker.dto.listing.ListingRequest;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.enums.ListingStatus;
import com.villagebooker.enums.PaymentMethod;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import com.villagebooker.repo.ListingRepo;
import com.villagebooker.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class OwnerListingService {

    private final ListingRepo listingRepo;
    private final UserRepo userRepo;

    @Transactional
    public ListingResponse createAccommodation(Long ownerId, CreateAccommodationRequest request) {
        checkCommonRules(request);
        if (request.baseGuests() < request.minGuests() || request.baseGuests() > request.maxGuests()) {
            throw new BadRequestException(ErrorCode.BASE_GUESTS_OUT_OF_RANGE,
                    "baseGuests must be between minGuests and maxGuests.",
                    Map.of("minGuests", request.minGuests(), "maxGuests", request.maxGuests()));
        }

        Accommodation accommodation = new Accommodation();
        copyCommonFields(request, accommodation, ownerId);
        accommodation.setBasePricePerNight(request.basePricePerNight());
        accommodation.setBaseGuests(request.baseGuests());
        accommodation.setExtraGuestPrice(request.extraGuestPrice());
        accommodation.setDoubleBedCount(request.doubleBedCount());
        accommodation.setSingleBedCount(request.singleBedCount());
        accommodation.setRoomCount(request.roomCount());

        return ListingResponse.from(listingRepo.save(accommodation));
    }

    @Transactional
    public ListingResponse createTour(Long ownerId, CreateTourRequest request) {
        checkCommonRules(request);

        Tour tour = new Tour();
        copyCommonFields(request, tour, ownerId);
        tour.setDurationDays(request.durationDays());
        tour.setDifficulty(request.difficulty());
        tour.setPricePerPerson(request.pricePerPerson());

        return ListingResponse.from(listingRepo.save(tour));
    }

    // "moji oglasi": every status except DELETED, newest first
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> findMine(Long ownerId, Pageable pageable) {
        return PageResponse.of(
                listingRepo.findByOwnerIdAndStatusNot(ownerId, ListingStatus.DELETED, pageable),
                ListingSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ListingResponse getMine(Long ownerId, Long listingId) {
        Listing listing = listingRepo.findById(listingId)
                .filter(l -> l.getStatus() != ListingStatus.DELETED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing " + listingId + " not found."));
        if (!listing.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.NOT_LISTING_OWNER, "Listing " + listingId + " belongs to another host.");
        }
        return ListingResponse.from(listing);
    }

    // rules that span several fields (single-field limits are Bean Validation on the request)
    private static void checkCommonRules(ListingRequest request) {
        if (request.minGuests() > request.maxGuests()) {
            throw new BadRequestException(ErrorCode.GUEST_RANGE_INVALID, "minGuests must not be greater than maxGuests.");
        }

        // cancellation terms must match the policy (refund table in spec section 3)
        switch (request.cancellationPolicy()) {
            // free until N days before arrival, nothing afterwards
            case FREE -> {
                requireFreeCancellationDays(request);
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

    private static void requireFreeCancellationDays(ListingRequest request) {
        if (request.freeCancellationDays() == null) {
            throw new BadRequestException(ErrorCode.FREE_CANCELLATION_DAYS_REQUIRED,
                    "FREE cancellation policy needs freeCancellationDays.");
        }
    }

    private static void forbidRefundPercentage(ListingRequest request) {
        if (request.refundPercentage() != null) {
            throw new BadRequestException(ErrorCode.REFUND_PERCENTAGE_NOT_ALLOWED,
                    "refundPercentage is only used with PARTIAL cancellation policy.");
        }
    }

    private void copyCommonFields(ListingRequest request, Listing listing, Long ownerId) {
        listing.setOwner(userRepo.getReferenceById(ownerId));
        listing.setStatus(ListingStatus.PENDING);
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
