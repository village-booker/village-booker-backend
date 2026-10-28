package com.villagebooker.controller.listing;

import com.villagebooker.controller.common.Paging;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AdminListingResponse;
import com.villagebooker.dto.listing.ListingSummaryResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.listing.AdminListingService;
import com.villagebooker.service.listing.ListingImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/listings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminListingService adminListingService;
    private final ListingImageService listingImageService;

    @GetMapping
    public PageResponse<ListingSummaryResponse> findByStatus(@RequestParam(defaultValue = "PENDING") ListingStatus status,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return adminListingService.findByStatus(status, Paging.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
    }

    @GetMapping("/{id}")
    public AdminListingResponse get(@PathVariable Long id) {
        return adminListingService.get(id);
    }

    @PostMapping("/{id}/approve")
    public AdminListingResponse approve(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return adminListingService.approve(authUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public AdminListingResponse reject(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                       @Valid @RequestBody RejectionRequest request) {
        return adminListingService.reject(authUser.id(), id, request);
    }

    // moderation: removes an inappropriate image (owners change images without a change request)
    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        listingImageService.deleteAsAdmin(id, imageId);
    }
}
