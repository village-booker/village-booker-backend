package com.villagebooker.controller.booking;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.booking.AccommodationBookingRequest;
import com.villagebooker.dto.booking.BookingResponse;
import com.villagebooker.dto.booking.TourBookingRequest;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.booking.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/accommodations")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createAccommodation(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody AccommodationBookingRequest request) {
        return bookingService.createAccommodation(authUser.id(), request);
    }

    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createTour(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody TourBookingRequest request) {
        return bookingService.createTour(authUser.id(), request);
    }

    @GetMapping
    public PageResponse<BookingResponse> findMine(@AuthenticationPrincipal AuthUser authUser,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return bookingService.findMine(authUser.id(),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    public BookingResponse getMine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return bookingService.getMine(authUser.id(), id);
    }
}
