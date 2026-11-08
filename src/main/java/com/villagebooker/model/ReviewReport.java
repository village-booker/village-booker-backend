package com.villagebooker.model;

import com.villagebooker.enums.review.ReviewReportStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// A user tells the admin that a review is offensive or false. One report per user and review, also after a decision.
@Entity
@Getter
@Setter
@Table(name = "review_report",
        uniqueConstraints = @UniqueConstraint(name = "uq_review_report_reporter",
                columnNames = {"review_id", "reporter_id"}),
        indexes = {
                @Index(name = "idx_review_report_status", columnList = "status, created_at"),
                @Index(name = "idx_review_report_reporter", columnList = "reporter_id")
        })
@AllArgsConstructor
@NoArgsConstructor
public class ReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewReportStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // set with the admin's decision (DISMISSED / UPHELD)
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;
}
