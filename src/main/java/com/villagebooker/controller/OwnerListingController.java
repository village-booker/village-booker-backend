package com.villagebooker.controller;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.CreateAccommodationRequest;
import com.villagebooker.dto.listing.CreateTourRequest;
import com.villagebooker.dto.listing.ListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.OwnerListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final OwnerListingService ownerListingService;

    @PostMapping("/accommodations")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse createAccommodation(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody CreateAccommodationRequest request) {
        return ownerListingService.createAccommodation(authUser.id(), request);
    }

    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse createTour(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody CreateTourRequest request) {
        return ownerListingService.createTour(authUser.id(), request);
    }

    @GetMapping
    public PageResponse<ListingSummaryResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        return ownerListingService.findMine(authUser.id(), pageable);
    }

    @GetMapping("/{id}")
    public ListingResponse getMine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ownerListingService.getMine(authUser.id(), id);
    }
}
