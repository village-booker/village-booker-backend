package com.villagebooker.controller.booking;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.booking.AdminBookingDetailResponse;
import com.villagebooker.dto.booking.AdminBookingResponse;
import com.villagebooker.dto.booking.AdminCancellationRequest;
import com.villagebooker.dto.booking.CancellationResponse;
import com.villagebooker.dto.booking.OwnerCancellationStatsResponse;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.service.booking.AdminBookingService;
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
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBookingController {

    private static final int MAX_MONTHS = 24;

    private final AdminBookingService adminBookingService;

    // newest first; every filter is optional
    @GetMapping
    public PageResponse<AdminBookingResponse> findAll(@RequestParam(required = false) BookingStatus status,
                                                      @RequestParam(required = false) Long listingId,
                                                      @RequestParam(required = false) Long guestId,
                                                      @RequestParam(required = false) Long ownerId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return adminBookingService.findAll(status, listingId, guestId, ownerId,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    public AdminBookingDetailResponse get(@PathVariable Long id) {
        return adminBookingService.get(id);
    }

    // any active booking, also one that has begun; the refund percentage is the admin's decision
    @PostMapping("/{id}/cancel")
    public CancellationResponse cancel(@PathVariable Long id, @Valid @RequestBody AdminCancellationRequest request) {
        return adminBookingService.cancel(id, request);
    }

    // hosts by CANCELLED_BY_OWNER bookings in the last ?months= (1..24, clamped like the page size)
    @GetMapping("/owner-cancellations")
    public PageResponse<OwnerCancellationStatsResponse> ownerCancellations(
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminBookingService.ownerCancellations(Math.clamp(months, 1, MAX_MONTHS),
                Paging.of(page, size, Sort.unsorted()));
    }
}
