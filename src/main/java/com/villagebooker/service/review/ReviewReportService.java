package com.villagebooker.service.review;

import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ErrorCode;
import com.villagebooker.dto.common.PageResponse;
import com.villagebooker.dto.review.ReportedReviewResponse;
import com.villagebooker.dto.review.ReviewReportRequest;
import com.villagebooker.dto.review.ReviewReportResponse;
import com.villagebooker.dto.review.ReviewResponse;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.review.ReviewReportStatus;
import com.villagebooker.model.Review;
import com.villagebooker.model.ReviewReport;
import com.villagebooker.repo.review.ReviewReportRepo;
import com.villagebooker.repo.review.ReviewReportRepo.PendingReview;
import com.villagebooker.repo.review.ReviewRepo;
import com.villagebooker.repo.user.UserRepo;
import com.villagebooker.service.auth.EmailVerificationService;
import com.villagebooker.service.listing.PublicListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Users report offensive or false reviews; the admin removes the review (AdminReviewService.delete, reports UPHELD)
// or dismisses the reports.
@Service
@RequiredArgsConstructor
public class ReviewReportService {

    private final ReviewReportRepo reportRepo;
    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;
    private final Clock clock;
    private final EmailVerificationService emailVerification;

    // any signed-in user who can see the review, the listing's host too; not its author
    @Transactional
    public ReviewReportResponse report(Long userId, Long reviewId, ReviewReportRequest request) {
        emailVerification.requireVerified(userId);
        Review review = reviewRepo.findWithBookingById(reviewId)
                .filter(r -> r.getDeletedAt() == null)
                .filter(r -> PublicListingService.VISIBLE.contains(r.getBooking().getListing().getStatus()))
                .orElseThrow(() -> ReviewService.reviewNotFound(reviewId));
        if (review.getBooking().getGuest().getId().equals(userId)) {
            throw new BadRequestException(ErrorCode.CANNOT_REPORT_OWN_REVIEW, "You cannot report your own review.");
        }
        // a report sent at the same moment fails on the unique constraint (409 DATA_CONFLICT)
        if (reportRepo.existsByReviewIdAndReporterId(reviewId, userId)) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_REPORTED,
                    "You have already reported review " + reviewId + ".");
        }
        ReviewReport report = new ReviewReport();
        report.setReview(review);
        report.setReporter(userRepo.getReferenceById(userId));
        report.setReason(request.reason().trim());
        report.setStatus(ReviewReportStatus.PENDING);
        return ReviewReportResponse.from(reportRepo.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportedReviewResponse> findPending(Pageable pageable) {
        Page<PendingReview> page = reportRepo.findPendingReviews(ListingStatus.CLOSED, pageable);
        List<Long> ids = page.getContent().stream().map(PendingReview::reviewId).toList();
        Map<Long, Review> reviews = reviewRepo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));
        Map<Long, List<ReportedReviewResponse.Report>> reports = reportRepo
                .findByReviewIdInAndStatusOrderById(ids, ReviewReportStatus.PENDING).stream()
                .collect(Collectors.groupingBy(r -> r.getReview().getId(),
                        Collectors.mapping(ReportedReviewResponse.Report::from, Collectors.toList())));
        return PageResponse.of(page, p -> new ReportedReviewResponse(ReviewResponse.from(reviews.get(p.reviewId())),
                p.reports(), p.firstReportedAt(), reports.getOrDefault(p.reviewId(), List.of())));
    }

    // the review stays as it is
    @Transactional
    public void dismiss(Long adminId, Long reviewId) {
        if (!reviewRepo.existsById(reviewId)) {
            throw ReviewService.reviewNotFound(reviewId);
        }
        if (resolve(adminId, reviewId, ReviewReportStatus.DISMISSED) == 0) {
            throw new ConflictException(ErrorCode.REVIEW_NOT_REPORTED,
                    "Review " + reviewId + " has no reports waiting for a decision.");
        }
    }

    int resolve(Long adminId, Long reviewId, ReviewReportStatus status) {
        return reportRepo.resolvePending(reviewId, status, Instant.now(clock), userRepo.getReferenceById(adminId));
    }
}
