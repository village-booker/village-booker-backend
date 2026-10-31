package com.villagebooker.controller.booking;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.booking.BookingRejectionRequest;
import com.villagebooker.dto.booking.OwnerBookingResponse;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.booking.OwnerBookingService;
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
@RequestMapping("/api/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final OwnerBookingService ownerBookingService;

    // newest first; ?status=AWAITING_OWNER_APPROVAL gives the requests to answer
    @GetMapping
    public PageResponse<OwnerBookingResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestParam(required = false) BookingStatus status,
                                                       @RequestParam(required = false) Long listingId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ownerBookingService.findMine(authUser.id(), status, listingId,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    public OwnerBookingResponse getMine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ownerBookingService.getMine(authUser.id(), id);
    }

    @PostMapping("/{id}/approve")
    public OwnerBookingResponse approve(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ownerBookingService.approve(authUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public OwnerBookingResponse reject(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
                                       @Valid @RequestBody(required = false) BookingRejectionRequest request) {
        return ownerBookingService.reject(authUser.id(), id, request);
    }
}
