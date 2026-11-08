package com.villagebooker.dto.review;

import com.villagebooker.model.ReviewReport;
import com.villagebooker.model.User;

import java.time.Instant;
import java.util.List;

// one review in the admin's queue of reports, with its PENDING reports (oldest first)
public record ReportedReviewResponse(
        ReviewResponse review,
        long reportCount,
        Instant firstReportedAt,
        List<Report> reports
) {

    public record Report(Long id, Long reporterId, String reporterFirstName, String reporterLastName,
                         String reason, Instant createdAt) {

        public static Report from(ReviewReport report) {
            User reporter = report.getReporter();
            return new Report(report.getId(), reporter.getId(), reporter.getFirstName(), reporter.getLastName(),
                    report.getReason(), report.getCreatedAt());
        }
    }
}
