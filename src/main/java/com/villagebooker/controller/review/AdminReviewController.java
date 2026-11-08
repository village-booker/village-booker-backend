package com.villagebooker.controller.review;

import com.villagebooker.controller.common.Paging;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.ReportedReviewResponse;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.security.AuthUser;
import com.villagebooker.service.review.AdminReviewService;
import com.villagebooker.service.review.ReviewReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;
    private final ReviewReportService reviewReportService;

    @GetMapping
    public PageResponse<ReviewResponse> find(@RequestParam(required = false) Long listingId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return adminReviewService.find(listingId, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    // reviews with reports waiting for a decision, longest waiting first
    @GetMapping("/reported")
    public PageResponse<ReportedReviewResponse> findReported(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return reviewReportService.findPending(Paging.of(page, size, Sort.unsorted()));
    }

    // also closes its pending reports (UPHELD)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        adminReviewService.delete(authUser.id(), id);
    }

    // the review stays; its pending reports become DISMISSED
    @PostMapping("/{id}/reports/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissReports(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        reviewReportService.dismiss(authUser.id(), id);
    }

    @DeleteMapping("/{id}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReply(@PathVariable Long id) {
        adminReviewService.deleteReply(id);
    }
}
