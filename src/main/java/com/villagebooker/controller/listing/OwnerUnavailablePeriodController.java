package com.villagebooker.controller.listing;

import com.villagebooker.dto.listing.UnavailablePeriodRequest;
import com.villagebooker.dto.listing.UnavailablePeriodResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.listing.UnavailablePeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/listings/{listingId}/unavailable-periods")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerUnavailablePeriodController {

    private final UnavailablePeriodService unavailablePeriodService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnavailablePeriodResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long listingId,
                                            @Valid @RequestBody UnavailablePeriodRequest request) {
        return unavailablePeriodService.create(authUser.id(), listingId, request);
    }

    @GetMapping
    public List<UnavailablePeriodResponse> findUpcoming(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long listingId) {
        return unavailablePeriodService.findUpcoming(authUser.id(), listingId);
    }

    @DeleteMapping("/{periodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long listingId,
                       @PathVariable Long periodId) {
        unavailablePeriodService.delete(authUser.id(), listingId, periodId);
    }
}
