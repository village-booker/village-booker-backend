package com.villagebooker.controller.tour;

import com.villagebooker.dto.booking.OwnerCancellationRequest;
import com.villagebooker.dto.tour.TourDateRequest;
import com.villagebooker.dto.tour.TourDateResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.tour.TourDateService;
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
@RequestMapping("/api/owner/listings/tours/{tourId}/dates")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerTourDateController {

    private final TourDateService tourDateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TourDateResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long tourId,
                                   @Valid @RequestBody TourDateRequest request) {
        return tourDateService.create(authUser.id(), tourId, request);
    }

    @GetMapping
    public List<TourDateResponse> findUpcoming(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long tourId) {
        return tourDateService.findUpcoming(authUser.id(), tourId);
    }

    // the date and all its bookings, guests get everything back
    @PostMapping("/{dateId}/cancel")
    public TourDateResponse cancel(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long tourId,
                                   @PathVariable Long dateId, @Valid @RequestBody OwnerCancellationRequest request) {
        return tourDateService.cancel(authUser.id(), tourId, dateId, request);
    }

    @DeleteMapping("/{dateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long tourId,
                       @PathVariable Long dateId) {
        tourDateService.delete(authUser.id(), tourId, dateId);
    }
}
