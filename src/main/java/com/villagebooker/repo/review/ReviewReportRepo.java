package com.villagebooker.repo.review;

import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.review.ReviewReportStatus;
import com.villagebooker.model.ReviewReport;
import com.villagebooker.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReviewReportRepo extends JpaRepository<ReviewReport, Long> {

    // one row per reported review in the admin queue
    record PendingReview(Long reviewId, long reports, Instant firstReportedAt) {
    }

    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    // admin queue: reviews with PENDING reports, longest waiting first; without removed reviews and reviews of
    // deleted / removed listings. Pageable without a sort: the query orders itself.
    @Query(value = """
            select new com.villagebooker.repo.review.ReviewReportRepo$PendingReview(
                r.review.id, count(r), min(r.createdAt))
            from ReviewReport r
            where r.status = com.villagebooker.enums.review.ReviewReportStatus.PENDING
              and r.review.deletedAt is null
              and r.review.booking.listing.status not in :closed
            group by r.review.id
            order by min(r.createdAt), r.review.id
            """,
            countQuery = """
            select count(distinct r.review.id) from ReviewReport r
            where r.status = com.villagebooker.enums.review.ReviewReportStatus.PENDING
              and r.review.deletedAt is null
              and r.review.booking.listing.status not in :closed
            """)
    Page<PendingReview> findPendingReviews(@Param("closed") Collection<ListingStatus> closed, Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ReviewReport> findByReviewIdInAndStatusOrderById(Collection<Long> reviewIds, ReviewReportStatus status);

    // the admin's decision closes every PENDING report of the review at once
    @Modifying(flushAutomatically = true)
    @Query("""
            update ReviewReport r set r.status = :status, r.resolvedAt = :at, r.resolvedBy = :admin
            where r.review.id = :reviewId
              and r.status = com.villagebooker.enums.review.ReviewReportStatus.PENDING
            """)
    int resolvePending(@Param("reviewId") Long reviewId, @Param("status") ReviewReportStatus status,
                       @Param("at") Instant at, @Param("admin") User admin);
}
