package com.villagebooker.dto.review;

import com.villagebooker.enums.review.ReviewReportStatus;
import com.villagebooker.model.ReviewReport;

import java.time.Instant;

// what the reporter gets back
public record ReviewReportResponse(Long id, Long reviewId, String reason, ReviewReportStatus status, Instant createdAt) {

    public static ReviewReportResponse from(ReviewReport report) {
        return new ReviewReportResponse(report.getId(), report.getReview().getId(), report.getReason(),
                report.getStatus(), report.getCreatedAt());
    }
}
