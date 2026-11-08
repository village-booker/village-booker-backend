package com.villagebooker.controller.review;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.PublicReviewResponse;
import com.villagebooker.dto.review.ReviewReportRequest;
import com.villagebooker.dto.review.ReviewReportResponse;
import com.villagebooker.dto.review.ReviewRequest;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.review.ReviewReportService;
import com.villagebooker.service.review.ReviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewReportService reviewReportService;

    // to the admin: an offensive or false review (also by the listing's host)
    @PostMapping("/api/reviews/{reviewId}/reports")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewReportResponse report(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long reviewId,
                                       @Valid @RequestBody ReviewReportRequest request) {
        return reviewReportService.report(authUser.id(), reviewId, request);
    }

    @PostMapping("/api/bookings/{bookingId}/review")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long bookingId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(authUser.id(), bookingId, request);
    }

    @GetMapping("/api/bookings/{bookingId}/review")
    @PreAuthorize("hasRole('USER')")
    public ReviewResponse getMine(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long bookingId) {
        return reviewService.getMine(authUser.id(), bookingId);
    }

    // public, no token (SecurityConfig); newest first
    @GetMapping("/api/listings/{listingId}/reviews")
    public PageResponse<PublicReviewResponse> findForListing(@PathVariable Long listingId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return reviewService.findForListing(listingId,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
}
