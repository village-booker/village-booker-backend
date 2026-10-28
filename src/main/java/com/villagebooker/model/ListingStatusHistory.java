package com.villagebooker.model;

import com.villagebooker.enums.listing.ListingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// Status history of a listing (PENDING -> APPROVED, REJECTED -> APPROVED, APPROVED -> PAUSED_BY_OWNER...).
// Changes to the listing's content are recorded in ListingChangeRequest (requestedChanges / previousValues);
// edits of a PENDING listing that no admin has seen yet are not recorded. Rows are never changed or deleted.
@Entity
@Getter
@Setter
@Table(name = "listing_status_history", indexes = {
        @Index(name = "idx_listing_status_history_listing", columnList = "listing_id"),
        @Index(name = "idx_listing_status_history_changed_by", columnList = "changed_by_id")
})
@AllArgsConstructor
@NoArgsConstructor
public class ListingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false)
    private ListingStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private ListingStatus newStatus;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    // e.g. the rejection reason, or "Change request #12 approved."
    @Column(name = "reason", length = 1000)
    private String reason;

    // who changed the status (admin or owner); null = the system (scheduler...)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;
}
