package com.villagebooker.controller.listing;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AvailabilityResponse;
import com.villagebooker.dto.listing.ListingCardResponse;
import com.villagebooker.dto.listing.ListingSearchRequest;
import com.villagebooker.dto.listing.PublicListingResponse;
import com.villagebooker.service.listing.PublicListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// public, no token (SecurityConfig)
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class PublicListingController {

    private final PublicListingService publicListingService;

    @GetMapping
    public PageResponse<ListingCardResponse> search(@Valid @ModelAttribute ListingSearchRequest request,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return publicListingService.search(request, Paging.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/{id}")
    public PublicListingResponse get(@PathVariable Long id) {
        return publicListingService.get(id);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse availability(@PathVariable Long id,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to) {
        return publicListingService.availability(id, from, to);
    }
}
