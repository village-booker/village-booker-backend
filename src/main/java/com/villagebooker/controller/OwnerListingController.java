package com.villagebooker.controller;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AccommodationRequest;
import com.villagebooker.dto.listing.ChangeRequestResponse;
import com.villagebooker.dto.listing.TourRequest;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.ChangeRequestService;
import com.villagebooker.service.OwnerListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// The host's own listings. Public search will be /api/listings, admin moderation /api/admin/listings.
@RestController
@RequestMapping("/api/owner/listings")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerListingController {

    private final OwnerListingService ownerListingService;
    private final ChangeRequestService changeRequestService;

    @PostMapping("/accommodations")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse createAccommodation(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody AccommodationRequest request) {
        return ownerListingService.createAccommodation(authUser.id(), request);
    }

    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse createTour(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody TourRequest request) {
        return ownerListingService.createTour(authUser.id(), request);
    }

    // full replace of a PENDING listing (not reviewed yet); other statuses use change requests below
    @PutMapping("/accommodations/{id}")
    public ListingResponse updateAccommodation(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                               @Valid @RequestBody AccommodationRequest request) {
        return ownerListingService.updateAccommodation(authUser.id(), id, request);
    }

    @PutMapping("/tours/{id}")
    public ListingResponse updateTour(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                      @Valid @RequestBody TourRequest request) {
        return ownerListingService.updateTour(authUser.id(), id, request);
    }

    // change of a listing an admin has already decided on: whole listing in the body, only changed fields are stored
    @PostMapping("/accommodations/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse requestAccommodationChange(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody AccommodationRequest request) {
        return changeRequestService.requestAccommodationChange(authUser.id(), id, request);
    }

    @PostMapping("/tours/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse requestTourChange(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody TourRequest request) {
        return changeRequestService.requestTourChange(authUser.id(), id, request);
    }

    // change requests of one own listing, newest first
    @GetMapping("/{id}/change-requests")
    public PageResponse<ChangeRequestResponse> findChangeRequests(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PathVariable Long id,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return changeRequestService.findForOwnListing(authUser.id(), id,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping
    public PageResponse<ListingSummaryResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ownerListingService.findMine(authUser.id(), Paging.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    @GetMapping("/{id}")
    public ListingResponse getMine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ownerListingService.getMine(authUser.id(), id);
    }
}
