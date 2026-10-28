package com.villagebooker.controller.listing;

import com.villagebooker.controller.common.Paging;

import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.listing.ChangeRequestResponse;
import com.villagebooker.dto.listing.RejectionRequest;
import com.villagebooker.enums.listing.ChangeRequestStatus;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.listing.ChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/change-requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminChangeRequestController {

    private final ChangeRequestService changeRequestService;

    @GetMapping
    public PageResponse<ChangeRequestResponse> findByStatus(
            @RequestParam(defaultValue = "PENDING") ChangeRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return changeRequestService.findByStatus(status, Paging.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
    }

    @GetMapping("/{id}")
    public ChangeRequestResponse get(@PathVariable Long id) {
        return changeRequestService.get(id);
    }

    @PostMapping("/{id}/approve")
    public ChangeRequestResponse approve(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return changeRequestService.approve(authUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public ChangeRequestResponse reject(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                        @Valid @RequestBody RejectionRequest request) {
        return changeRequestService.reject(authUser.id(), id, request);
    }
}
