package com.villagebooker.controller.listing;

import com.villagebooker.controller.common.Paging;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.AdminImageResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.listing.ImageStatus;
import com.villagebooker.service.listing.ListingImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/images")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminImageController {

    private final ListingImageService listingImageService;

    @GetMapping
    public PageResponse<AdminImageResponse> findByStatus(@RequestParam(defaultValue = "PENDING") ImageStatus status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return listingImageService.findByStatus(status, Paging.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
    }

    @PostMapping("/{id}/approve")
    public AdminImageResponse approve(@PathVariable Long id) {
        return listingImageService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public AdminImageResponse reject(@PathVariable Long id, @Valid @RequestBody RejectionRequest request) {
        return listingImageService.reject(id, request);
    }
}
